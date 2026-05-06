package com.example.bidmart.order.listener;

import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionWon(AuctionWonEvent event) {
        orderService.createOrderAutomatically(event.listingId(), event.winnerId());
    }
}