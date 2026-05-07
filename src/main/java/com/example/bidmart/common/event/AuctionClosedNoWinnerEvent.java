package com.example.bidmart.common.event;

import java.util.UUID;

public record AuctionClosedNoWinnerEvent(
        UUID listingId,
        String reason
) {}
