package com.example.bidmart.bidding.service;

import com.example.bidmart.listing.model.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ListingSnapshot(
        UUID id,
        UUID sellerId,
        BigDecimal startingPrice,
        LocalDateTime endTime,
        AuctionStatus auctionStatus,
        BigDecimal currentHighestBid,
        UUID currentHighestBidderId
) {

    public boolean isOpenAt(LocalDateTime currentTime) {
        return auctionStatus.isAcceptingBids() && (endTime == null || endTime.isAfter(currentTime));
    }
}
