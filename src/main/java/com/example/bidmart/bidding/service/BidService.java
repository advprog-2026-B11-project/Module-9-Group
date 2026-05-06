package com.example.bidmart.bidding.service;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.exception.ResourceNotFoundException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.common.event.BidPlacedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BidService {

    private final BidRepository bidRepository;
    private final ListingLookupService listingLookupService;
    private final MockWalletService mockWalletService;
    private final ApplicationEventPublisher eventPublisher;

    public BidService(
            BidRepository bidRepository,
            ListingLookupService listingLookupService,
            MockWalletService mockWalletService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.bidRepository = bidRepository;
        this.listingLookupService = listingLookupService;
        this.mockWalletService = mockWalletService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public BidResponse placeBid(CreateBidRequest request) {
        validateCreateBidRequest(request);

        ListingSnapshot listing = listingLookupService.findById(request.listingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing tidak ditemukan: " + request.listingId()));

        validateListingForBid(listing, request.buyerId());

        Optional<Bid> currentHighestBid = bidRepository
                .findTopByListingIdOrderByAmountDescCreatedAtAsc(request.listingId());

        validateBidAmount(request.amount(), listing, currentHighestBid);

        boolean proxyBid = Boolean.TRUE.equals(request.proxyBid());
        BigDecimal reserveTarget = resolveReserveTarget(request.amount(), proxyBid, request.proxyMaxLimit());

        Optional<Bid> latestBidByBuyer = bidRepository
                .findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(request.listingId(), request.buyerId());

        BigDecimal previousReservedAmount = latestBidByBuyer
                .map(Bid::getReservedAmount)
                .orElse(BigDecimal.ZERO);

        mockWalletService.reserveBidFunds(
                request.buyerId(),
                request.listingId(),
                reserveTarget.max(previousReservedAmount)
        );

        Bid bid = new Bid();
        bid.setListingId(request.listingId());
        bid.setBuyerId(request.buyerId());
        bid.setAmount(request.amount());
        bid.setProxyBid(proxyBid);
        bid.setProxyMaxLimit(proxyBid ? request.proxyMaxLimit() : null);

        Bid savedBid = bidRepository.save(bid);
        releasePreviousHighestBidIfOutbid(currentHighestBid, savedBid);

        // 5. Publish event tepat setelah bid berhasil disimpan ke database
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
                .orElseThrow(() -> new ResourceNotFoundException("Belum ada bid untuk listing: " + listingId));

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

    private void validateCreateBidRequest(CreateBidRequest request) {
        if (request == null) {
            throw new BidValidationException("Request bid tidak boleh kosong.");
        }

        if (request.listingId() == null) {
            throw new BidValidationException("listingId wajib diisi.");
        }

        if (request.buyerId() == null) {
            throw new BidValidationException("buyerId wajib diisi.");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BidValidationException("Amount wajib lebih dari 0.");
        }

        if (Boolean.TRUE.equals(request.proxyBid())) {
            if (request.proxyMaxLimit() == null || request.proxyMaxLimit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BidValidationException("proxyMaxLimit wajib diisi dan lebih dari 0 untuk proxy bid.");
            }

            if (request.proxyMaxLimit().compareTo(request.amount()) < 0) {
                throw new BidValidationException("proxyMaxLimit tidak boleh lebih kecil dari amount.");
            }
        }
    }

    private void validateListingForBid(ListingSnapshot listing, UUID buyerId) {
        if (listing.sellerId() != null && listing.sellerId().equals(buyerId)) {
            throw new BidValidationException("Seller tidak boleh melakukan bid pada listing miliknya sendiri.");
        }

        if (!listing.isOpenAt(LocalDateTime.now())) {
            throw new BidValidationException("Listing sudah tidak aktif atau waktu lelang telah berakhir.");
        }
    }

    private void validateBidAmount(BigDecimal bidAmount, ListingSnapshot listing, Optional<Bid> currentHighestBid) {
        BigDecimal startingPrice = listing.startingPrice() == null ? BigDecimal.ZERO : listing.startingPrice();

        if (bidAmount.compareTo(startingPrice) < 0) {
            throw new BidValidationException("Bid harus lebih besar atau sama dengan starting price " + startingPrice + ".");
        }

        if (currentHighestBid.isPresent() && bidAmount.compareTo(currentHighestBid.get().getAmount()) <= 0) {
            throw new BidValidationException("Bid harus lebih tinggi dari bid tertinggi saat ini.");
        }
    }

    private BigDecimal resolveReserveTarget(BigDecimal amount, boolean proxyBid, BigDecimal proxyMaxLimit) {
        if (!proxyBid) {
            return amount;
        }

        return proxyMaxLimit;
    }

    private void releasePreviousHighestBidIfOutbid(Optional<Bid> previousHighestBid, Bid latestSavedBid) {
        if (previousHighestBid.isEmpty()) {
            return;
        }

        Bid previous = previousHighestBid.get();

        if (previous.getBuyerId().equals(latestSavedBid.getBuyerId())) {
            return;
        }

        mockWalletService.releaseBidFunds(
                previous.getBuyerId(),
                previous.getListingId(),
                previous.getReservedAmount()
        );
    }
}