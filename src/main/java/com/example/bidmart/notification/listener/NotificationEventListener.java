package com.example.bidmart.notification.listener;

import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.common.event.BidPlacedEvent;
import com.example.bidmart.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionWon(AuctionWonEvent event) {
        String message = "Selamat! Anda memenangkan lelang dengan harga akhir Rp " + event.winningPrice();
        notificationService.createNotification(event.winnerId(), "AUCTION_WON", message);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNewBid(BidPlacedEvent event) {
        String message = "Penawaran berhasil ditempatkan sebesar Rp " + event.bidAmount();
        notificationService.createNotification(event.buyerId(), "NEW_BID", message);
    }
}