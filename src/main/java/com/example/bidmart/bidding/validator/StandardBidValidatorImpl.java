package com.example.bidmart.bidding.validator;

import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.service.ListingSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
public class StandardBidValidatorImpl implements BidRuleValidator {

    @Override
    public void validateRequest(CreateBidRequest request, UUID buyerId) {
        if (request == null) {
            throw new BidValidationException("Request bid tidak boleh kosong.");
        }

        if (request.listingId() == null) {
            throw new BidValidationException("listingId wajib diisi.");
        }

        if (buyerId == null) {
            throw new BidValidationException("buyerId wajib diisi.");
        }

        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BidValidationException("Amount wajib lebih dari 0.");
        }

        if (Boolean.TRUE.equals(request.proxyBid())) {
            if (request.proxyMaxLimit() == null || request.proxyMaxLimit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BidValidationException(
                        "proxyMaxLimit wajib diisi dan lebih dari 0 untuk proxy bid.");
            }

            if (request.proxyMaxLimit().compareTo(request.amount()) < 0) {
                throw new BidValidationException(
                        "proxyMaxLimit tidak boleh lebih kecil dari amount.");
            }
        }
    }

    @Override
    public void validateBidContext(
            UUID buyerId,
            ListingSnapshot listing,
            BigDecimal bidAmount,
            Optional<Bid> currentHighestBid
    ) {
        validateBuyerIsNotSeller(listing, buyerId);
        validateAuctionIsOpen(listing);
        validateBidAmount(bidAmount, listing, currentHighestBid);
    }

    // --- private helpers ---------------------------------------------------

    private void validateBuyerIsNotSeller(ListingSnapshot listing, UUID buyerId) {
        if (listing.sellerId() != null && listing.sellerId().equals(buyerId)) {
            throw new BidValidationException(
                    "Seller tidak boleh melakukan bid pada listing miliknya sendiri.");
        }
    }

    private void validateAuctionIsOpen(ListingSnapshot listing) {
        if (!listing.isOpenAt(LocalDateTime.now())) {
            throw new BidValidationException(
                    "Listing sudah tidak aktif atau waktu lelang telah berakhir.");
        }
    }

    private void validateBidAmount(
            BigDecimal bidAmount,
            ListingSnapshot listing,
            Optional<Bid> currentHighestBid
    ) {
        BigDecimal startingPrice = listing.startingPrice() == null
                ? BigDecimal.ZERO
                : listing.startingPrice();

        if (bidAmount.compareTo(startingPrice) < 0) {
            throw new BidValidationException(
                    "Bid harus lebih besar atau sama dengan starting price " + startingPrice + ".");
        }

        if (currentHighestBid.isPresent()
                && bidAmount.compareTo(currentHighestBid.get().getAmount()) <= 0) {
            throw new BidValidationException(
                    "Bid harus lebih tinggi dari bid tertinggi saat ini.");
        }
    }
}
