package com.example.bidmart.notification.listener;

import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.common.event.BidPlacedEvent;
import com.example.bidmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @EventListener
    public void handleAuctionWon(AuctionWonEvent event) {
        String message = "Selamat! Anda memenangkan lelang dengan harga akhir Rp " + event.winningPrice();
        notificationService.createNotification(event.winnerId(), "AUCTION_WON", message);
    }

    @EventListener
    public void handleNewBid(BidPlacedEvent event) {
        String message = "Penawaran berhasil ditempatkan sebesar Rp " + event.bidAmount();
        notificationService.createNotification(event.buyerId(), "NEW_BID", message);
    }
}