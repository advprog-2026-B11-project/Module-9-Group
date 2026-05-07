package com.example.bidmart.bidding.strategy;

import com.example.bidmart.bidding.service.ListingSnapshot;
import com.example.bidmart.listing.model.AuctionType;

import java.math.BigDecimal;

public interface AuctionStrategy {
    AuctionType getSupportedType();
    ValidationResult validateBid(BigDecimal bidAmount, ListingSnapshot listing);
    boolean requiresFundHolding();
    BigDecimal computeMinimumNextBid(ListingSnapshot listing);
}
