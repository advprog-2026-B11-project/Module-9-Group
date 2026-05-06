package com.example.bidmart.common.event;

import java.math.BigDecimal;
import java.util.UUID;

public record AuctionWonEvent(
        UUID listingId,
        UUID winnerId,
        BigDecimal winningPrice
) {}