package com.example.bidmart.bidding.service;

import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.common.event.AuctionClosedNoWinnerEvent;
import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.listing.model.AuctionStatus;
import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.repository.ListingRepository;
import com.example.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionClosingServiceTest {

    @Mock private ListingRepository     listingRepository;
    @Mock private BidRepository         bidRepository;
    @Mock private WalletService         walletService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AuctionClosingService auctionClosingService;

    private Listing activeListing(UUID id, BigDecimal currentHighestBid,
                                  UUID currentHighestBidderId, BigDecimal reservePrice) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setStatus(AuctionStatus.ACTIVE);
        listing.setEndTime(LocalDateTime.now().minusMinutes(5));
        listing.setCurrentHighestBid(currentHighestBid);
        listing.setCurrentHighestBidderId(currentHighestBidderId);
        listing.setReservePrice(reservePrice);
        return listing;
    }

    private Bid makeBid(UUID listingId, UUID buyerId, BigDecimal amount) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setListingId(listingId);
        bid.setBuyerId(buyerId);
        bid.setAmount(amount);
        bid.setProxyBid(false);
        return bid;
    }

    @Test
    void closeAuction_withWinner_reservePriceMet_setsStatusWonAndPublishesAuctionWonEvent() {
        UUID listingId = UUID.randomUUID();
        UUID winnerId  = UUID.randomUUID();
        BigDecimal winningBid = new BigDecimal("500000");

        Listing listing = activeListing(listingId, winningBid, winnerId, new BigDecimal("400000"));

        when(bidRepository.findByListingIdOrderByCreatedAtDesc(listingId))
                .thenReturn(List.of(makeBid(listingId, winnerId, winningBid)));

        auctionClosingService.closeAuction(listing);

        assertThat(listing.getStatus()).isEqualTo(AuctionStatus.WON);
        verify(listingRepository).save(listing);
        verify(walletService).settlePayment(winnerId, winningBid, listingId.toString());
        verify(walletService, never()).releaseBidFunds(eq(winnerId), any(), any());
        verify(eventPublisher).publishEvent(any(AuctionWonEvent.class));
    }

    @Test
    void closeAuction_withBid_reservePriceNotMet_setsStatusUnsoldAndReleasesAllHolds() {
        UUID listingId = UUID.randomUUID();
        UUID buyerId   = UUID.randomUUID();
        BigDecimal bidAmount = new BigDecimal("300000");

        Listing listing = activeListing(listingId, bidAmount, buyerId, new BigDecimal("500000"));

        when(bidRepository.findByListingIdOrderByCreatedAtDesc(listingId))
                .thenReturn(List.of(makeBid(listingId, buyerId, bidAmount)));

        auctionClosingService.closeAuction(listing);

        assertThat(listing.getStatus()).isEqualTo(AuctionStatus.UNSOLD);
        verify(listingRepository).save(listing);
        verify(walletService).releaseBidFunds(buyerId, listingId, bidAmount);
        verify(eventPublisher).publishEvent(any(AuctionClosedNoWinnerEvent.class));
    }

    @Test
    void closeAuction_noBids_setsStatusUnsold() {
        UUID listingId = UUID.randomUUID();

        Listing listing = activeListing(listingId, null, null, null);

        when(bidRepository.findByListingIdOrderByCreatedAtDesc(listingId))
                .thenReturn(List.of());

        auctionClosingService.closeAuction(listing);

        assertThat(listing.getStatus()).isEqualTo(AuctionStatus.UNSOLD);
        verify(listingRepository).save(listing);
        verify(walletService, never()).releaseBidFunds(any(), any(), any());
        verify(walletService, never()).settlePayment(any(), any(), any());
        verify(eventPublisher).publishEvent(any(AuctionClosedNoWinnerEvent.class));
    }

    @Test
    void closeAuction_alreadyFinalStatus_doesNothing() {
        UUID listingId = UUID.randomUUID();

        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setStatus(AuctionStatus.WON);

        auctionClosingService.closeAuction(listing);

        verify(listingRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(walletService, never()).settlePayment(any(), any(), any());
        verify(walletService, never()).releaseBidFunds(any(), any(), any());
    }

    @Test
    void closeAuction_releaseLoserHoldsFails_continuesForOtherBidders() {
        UUID listingId  = UUID.randomUUID();
        UUID winnerId   = UUID.randomUUID();
        UUID loser1Id   = UUID.randomUUID();
        UUID loser2Id   = UUID.randomUUID();
        BigDecimal winningBid  = new BigDecimal("500000");
        BigDecimal loser1Bid   = new BigDecimal("300000");
        BigDecimal loser2Bid   = new BigDecimal("200000");

        Listing listing = activeListing(listingId, winningBid, winnerId, new BigDecimal("100000"));

        when(bidRepository.findByListingIdOrderByCreatedAtDesc(listingId))
                .thenReturn(List.of(
                        makeBid(listingId, winnerId, winningBid),
                        makeBid(listingId, loser1Id, loser1Bid),
                        makeBid(listingId, loser2Id, loser2Bid)
                ));
        doThrow(new RuntimeException("Wallet error"))
                .when(walletService).releaseBidFunds(eq(loser1Id), any(), any());

        auctionClosingService.closeAuction(listing);

        verify(walletService, never()).releaseBidFunds(eq(winnerId), any(), any());
        verify(walletService).releaseBidFunds(eq(loser1Id), eq(listingId), eq(loser1Bid));
        verify(walletService).releaseBidFunds(eq(loser2Id), eq(listingId), eq(loser2Bid));
        verify(eventPublisher).publishEvent(any(AuctionWonEvent.class));
    }
}
