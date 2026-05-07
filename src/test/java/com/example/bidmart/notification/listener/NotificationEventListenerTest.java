package com.example.bidmart.notification.listener;

import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.common.event.BidPlacedEvent;
import com.example.bidmart.common.event.OutbidEvent;
import com.example.bidmart.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Test
    void handleAuctionWon_success() {
        UUID listingId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        BigDecimal winningPrice = new BigDecimal("150000.00");
        AuctionWonEvent event = new AuctionWonEvent(listingId, winnerId, winningPrice);

        notificationEventListener.handleAuctionWon(event);

        verify(notificationService, times(1)).createNotification(
                eq(winnerId),
                eq("AUCTION_WON"),
                anyString()
        );
    }

    @Test
    void handleNewBid_success() {
        UUID listingId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        BigDecimal bidAmount = new BigDecimal("50000.00");
        BidPlacedEvent event = new BidPlacedEvent(listingId, buyerId, bidAmount);

        notificationEventListener.handleNewBid(event);

        verify(notificationService, times(1)).createNotification(
                eq(buyerId),
                eq("NEW_BID"),
                anyString()
        );
    }

    @Test
    void handleOutbid_success() {
        UUID listingId = UUID.randomUUID();
        UUID outbidUserId = UUID.randomUUID();
        BigDecimal newHighestBid = new BigDecimal("75000.00");
        OutbidEvent event = new OutbidEvent(listingId, outbidUserId, newHighestBid);

        notificationEventListener.handleOutbid(event);

        verify(notificationService, times(1)).createNotification(
                eq(outbidUserId),
                eq("OUTBID"),
                anyString()
        );
    }
}
