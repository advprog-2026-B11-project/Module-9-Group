package com.example.bidmart.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record AuctionExtendedEvent(
        UUID listingId,
        LocalDateTime newEndTime
) {}
