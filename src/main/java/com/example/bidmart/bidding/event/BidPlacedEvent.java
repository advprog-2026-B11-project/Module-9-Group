package com.example.bidmart.bidding.event;

import java.math.BigDecimal;
import java.util.UUID;

public record BidPlacedEvent(
        UUID listingId,
        UUID buyerId,
        BigDecimal amount
) {}
