package com.example.bidmart.bidding.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBidRequest(
        UUID listingId,
        BigDecimal amount,
        Boolean proxyBid,
        BigDecimal proxyMaxLimit
) {
}
