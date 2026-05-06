package com.example.bidmart.listing.model;

public enum AuctionStatus {
    DRAFT, ACTIVE, EXTENDED, CLOSED, WON, UNSOLD;

    public boolean isAcceptingBids() {
        return this == ACTIVE || this == EXTENDED;
    }

    public boolean isFinal() {
        return this == CLOSED || this == WON || this == UNSOLD;
    }

    public boolean isActive() {
        return this == ACTIVE || this == EXTENDED;
    }
}
