package com.example.bidmart.bidding.service;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.exception.BidConflictException;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.exception.ResourceNotFoundException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.bidding.strategy.AuctionStrategy;
import com.example.bidmart.bidding.strategy.AuctionStrategyRegistry;
import com.example.bidmart.bidding.strategy.ValidationResult;
import com.example.bidmart.bidding.validator.BidRuleValidator;
import com.example.bidmart.common.event.AuctionExtendedEvent;
import com.example.bidmart.common.event.BidPlacedEvent;
import com.example.bidmart.common.event.OutbidEvent;
import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.service.ListingService;
import com.example.bidmart.wallet.service.WalletService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BidService {

    private final BidRepository bidRepository;
    private final ListingService listingService;
    private final WalletService walletService;
    private final BidRuleValidator bidRuleValidator;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionStrategyRegistry strategyRegistry;

    public BidService(
            BidRepository bidRepository,
            ListingService listingService,
            WalletService walletService,
            BidRuleValidator bidRuleValidator,
            ApplicationEventPublisher eventPublisher,
            AuctionStrategyRegistry strategyRegistry
    ) {
        this.bidRepository = bidRepository;
        this.listingService = listingService;
        this.walletService = walletService;
        this.bidRuleValidator = bidRuleValidator;
        this.eventPublisher = eventPublisher;
        this.strategyRegistry = strategyRegistry;
    }

    @Retryable(
            retryFor = {ObjectOptimisticLockingFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 50, multiplier = 2)
    )
    @Transactional
    public BidResponse placeBid(UUID buyerId, CreateBidRequest request) {
        bidRuleValidator.validateRequest(request, buyerId);

        Listing listingEntity = listingService.getListingByIdWithLock(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing tidak ditemukan: " + request.listingId()));

        ListingSnapshot listing = toSnapshot(listingEntity);
        AuctionStrategy strategy = strategyRegistry.getStrategy(listingEntity.getAuctionType());

        Optional<Bid> currentHighestBid = bidRepository
                .findTopByListingIdOrderByAmountDescCreatedAtAsc(request.listingId());

        bidRuleValidator.validateBidContext(buyerId, listing, request.amount(), currentHighestBid);

        ValidationResult strategyResult = strategy.validateBid(request.amount(), listing);
        if (!strategyResult.valid()) {
            throw new BidValidationException(strategyResult.errorMessage());
        }

        boolean proxyBid = Boolean.TRUE.equals(request.proxyBid());
        BigDecimal reserveTarget = proxyBid ? request.proxyMaxLimit() : request.amount();

        Optional<Bid> latestBidByBuyer = bidRepository
                .findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(request.listingId(), buyerId);

        BigDecimal previousReservedAmount = latestBidByBuyer
                .map(Bid::getReservedAmount)
                .orElse(BigDecimal.ZERO);

        BigDecimal additionalReserve = reserveTarget.max(previousReservedAmount)
                .subtract(previousReservedAmount);

        if (strategy.requiresFundHolding() && additionalReserve.compareTo(BigDecimal.ZERO) > 0) {
            walletService.reserveBidFunds(buyerId, request.listingId(), additionalReserve);
        }

        Bid bid = new Bid();
        bid.setListingId(request.listingId());
        bid.setBuyerId(buyerId);
        bid.setAmount(request.amount());
        bid.setProxyBid(proxyBid);
        bid.setProxyMaxLimit(proxyBid ? request.proxyMaxLimit() : null);

        Bid savedBid = bidRepository.save(bid);

        listingEntity.updateHighestBid(savedBid.getBuyerId(), savedBid.getAmount());

        boolean extended = false;
        if (listingEntity.isWithinAntiSnipingWindow()) {
            listingEntity.extendAuction();
            extended = true;
        }

        listingService.save(listingEntity);

        releaseOutbidFunds(currentHighestBid, savedBid)
                .ifPresent(outbid -> eventPublisher.publishEvent(
                        new OutbidEvent(savedBid.getListingId(), outbid.getBuyerId(), savedBid.getAmount())));

        eventPublisher.publishEvent(new BidPlacedEvent(
                savedBid.getListingId(),
                savedBid.getBuyerId(),
                savedBid.getAmount()
        ));

        if (extended) {
            eventPublisher.publishEvent(new AuctionExtendedEvent(
                    listingEntity.getId(), listingEntity.getEndTime()));
        }

        return BidResponse.from(savedBid);
    }

    @Recover
    public BidResponse recoverFromConflict(ObjectOptimisticLockingFailureException ex,
                                           UUID buyerId, CreateBidRequest request) {
        throw new BidConflictException("Terjadi konflik penawaran, silakan coba lagi dalam beberapa saat.");
    }

    public BigDecimal getMinimumNextBid(UUID listingId) {
        if (listingId == null) {
            throw new BidValidationException("listingId wajib diisi.");
        }

        Listing listingEntity = listingService.getListingById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing tidak ditemukan: " + listingId));

        AuctionStrategy strategy = strategyRegistry.getStrategy(listingEntity.getAuctionType());
        return strategy.computeMinimumNextBid(toSnapshot(listingEntity));
    }

    public List<BidResponse> getBidsByListing(UUID listingId) {
        if (listingId == null) {
            throw new BidValidationException("listingId wajib diisi.");
        }

        return bidRepository.findByListingIdOrderByCreatedAtDesc(listingId).stream()
                .map(BidResponse::from)
                .toList();
    }

    public BidResponse getHighestBid(UUID listingId) {
        if (listingId == null) {
            throw new BidValidationException("listingId wajib diisi.");
        }

        Bid highestBid = bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Belum ada bid untuk listing: " + listingId));

        return BidResponse.from(highestBid);
    }

    public List<BidResponse> getBidsByBuyer(UUID buyerId) {
        if (buyerId == null) {
            throw new BidValidationException("buyerId wajib diisi.");
        }

        return bidRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
                .map(BidResponse::from)
                .toList();
    }

    private ListingSnapshot toSnapshot(Listing listing) {
        BigDecimal startingPrice = listing.getStartingPrice() == null
                ? BigDecimal.ZERO
                : listing.getStartingPrice();

        return new ListingSnapshot(
                listing.getId(),
                listing.getSellerId(),
                startingPrice,
                listing.getEndTime(),
                listing.getStatus(),
                listing.getCurrentHighestBid(),
                listing.getCurrentHighestBidderId()
        );
    }

    private Optional<Bid> releaseOutbidFunds(Optional<Bid> previousHighestBid, Bid newBid) {
        if (previousHighestBid.isEmpty()) {
            return Optional.empty();
        }

        Bid previous = previousHighestBid.get();

        if (previous.getBuyerId().equals(newBid.getBuyerId())) {
            return Optional.empty();
        }

        walletService.releaseBidFunds(
                previous.getBuyerId(),
                previous.getListingId(),
                previous.getReservedAmount()
        );

        return Optional.of(previous);
    }
}
