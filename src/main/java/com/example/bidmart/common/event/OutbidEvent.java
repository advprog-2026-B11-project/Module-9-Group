package com.example.bidmart.common.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OutbidEvent(
        UUID listingId,
        UUID outbidUserId,
        BigDecimal newHighestBid
) {}
