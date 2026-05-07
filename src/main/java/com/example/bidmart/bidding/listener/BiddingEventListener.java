package com.example.bidmart.bidding.listener;

import com.example.bidmart.common.event.BidPlacedEvent;
import com.example.bidmart.common.event.OutbidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class BiddingEventListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBidPlaced(BidPlacedEvent event) {
        log.info("Event Received [BidPlaced]: Buyer [{}] placed a bid of [{}] on listing [{}]",
                event.buyerId(), event.bidAmount(), event.listingId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOutbid(OutbidEvent event) {
        log.info("Event Received [Outbid]: User [{}] was outbid on listing [{}] — new highest bid is [{}]",
                event.outbidUserId(), event.listingId(), event.newHighestBid());
    }
}
