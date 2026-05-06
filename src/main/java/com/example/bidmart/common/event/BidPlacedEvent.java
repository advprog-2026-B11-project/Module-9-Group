package com.example.bidmart.common.event;

import java.math.BigDecimal;
import java.util.UUID;

public record BidPlacedEvent(
        UUID listingId,
        UUID buyerId,
        BigDecimal bidAmount
) {}