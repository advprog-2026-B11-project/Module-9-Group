package com.example.bidmart.bidding.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OutbidEvent(
        UUID listingId,
        UUID oldBuyerId,
        BigDecimal newAmount
) {}
