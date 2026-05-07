package com.example.bidmart.bidding.strategy;

import com.example.bidmart.bidding.service.ListingSnapshot;
import com.example.bidmart.listing.model.AuctionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class EnglishAuctionStrategy implements AuctionStrategy {

    @Override
    public AuctionType getSupportedType() {
        return AuctionType.ENGLISH;
    }

    @Override
    public ValidationResult validateBid(BigDecimal bidAmount, ListingSnapshot listing) {
        if (listing.currentHighestBid() == null) {
            if (bidAmount.compareTo(listing.startingPrice()) < 0) {
                return ValidationResult.fail(
                        "Bid harus minimal sama dengan harga awal: " + listing.startingPrice());
            }
        } else {
            if (bidAmount.compareTo(listing.currentHighestBid()) <= 0) {
                return ValidationResult.fail(
                        "Bid harus lebih tinggi dari bid tertinggi saat ini: " + listing.currentHighestBid());
            }
        }
        return ValidationResult.ok();
    }

    @Override
    public boolean requiresFundHolding() {
        return true;
    }

    @Override
    public BigDecimal computeMinimumNextBid(ListingSnapshot listing) {
        if (listing.currentHighestBid() == null) {
            return listing.startingPrice();
        }
        return listing.currentHighestBid().add(BigDecimal.ONE);
    }
}
