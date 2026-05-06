package com.example.bidmart.order.listener;

import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;

    @EventListener
    public void handleAuctionWon(AuctionWonEvent event) {
        orderService.createOrderAutomatically(event.listingId(), event.winnerId());
    }
}