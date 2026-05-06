package com.example.bidmart.order.listener;

import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderEventListener orderEventListener;

    @Test
    void handleAuctionWon_success() {
        UUID listingId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        BigDecimal winningPrice = new BigDecimal("250000.00");
        AuctionWonEvent event = new AuctionWonEvent(listingId, winnerId, winningPrice);

        orderEventListener.handleAuctionWon(event);

        verify(orderService, times(1)).createOrderAutomatically(listingId, winnerId);
    }
}