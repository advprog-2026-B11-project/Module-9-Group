package com.example.bidmart.bidding.service;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.event.OutbidEvent;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.exception.ResourceNotFoundException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.bidding.validator.BidRuleValidator;
import com.example.bidmart.common.event.BidPlacedEvent;
import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.service.ListingService;
import com.example.bidmart.wallet.service.WalletService;
import org.springframework.context.ApplicationEventPublisher;
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

    public BidService(
            BidRepository bidRepository,
            ListingService listingService,
            WalletService walletService,
            BidRuleValidator bidRuleValidator,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bidRepository = bidRepository;
        this.listingService = listingService;
        this.walletService = walletService;
        this.bidRuleValidator = bidRuleValidator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public BidResponse placeBid(UUID buyerId, CreateBidRequest request) {
        bidRuleValidator.validateRequest(request, buyerId);

        ListingSnapshot listing = listingService.getListingById(request.listingId())
                .map(this::toSnapshot)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Listing tidak ditemukan: " + request.listingId()));

        Optional<Bid> currentHighestBid = bidRepository
                .findTopByListingIdOrderByAmountDescCreatedAtAsc(request.listingId());

        bidRuleValidator.validateBidContext(buyerId, listing, request.amount(), currentHighestBid);

        boolean proxyBid = Boolean.TRUE.equals(request.proxyBid());
        BigDecimal reserveTarget = proxyBid ? request.proxyMaxLimit() : request.amount();

        Optional<Bid> latestBidByBuyer = bidRepository
                .findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(request.listingId(), buyerId);

        BigDecimal previousReservedAmount = latestBidByBuyer
                .map(Bid::getReservedAmount)
                .orElse(BigDecimal.ZERO);

        BigDecimal additionalReserve = reserveTarget.max(previousReservedAmount)
                .subtract(previousReservedAmount);

        if (additionalReserve.compareTo(BigDecimal.ZERO) > 0) {
            walletService.reserveBidFunds(buyerId, request.listingId(), additionalReserve);
        }

        Bid bid = new Bid();
        bid.setListingId(request.listingId());
        bid.setBuyerId(buyerId);
        bid.setAmount(request.amount());
        bid.setProxyBid(proxyBid);
        bid.setProxyMaxLimit(proxyBid ? request.proxyMaxLimit() : null);

        Bid savedBid = bidRepository.save(bid);

        releaseOutbidFunds(currentHighestBid, savedBid)
                .ifPresent(outbid -> eventPublisher.publishEvent(
                        new OutbidEvent(savedBid.getListingId(), outbid.getBuyerId(), savedBid.getAmount())));

        eventPublisher.publishEvent(new BidPlacedEvent(
                savedBid.getListingId(),
                savedBid.getBuyerId(),
                savedBid.getAmount()
        ));

        return BidResponse.from(savedBid);
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
                listing.getStatus()
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