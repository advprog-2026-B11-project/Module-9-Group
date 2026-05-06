package com.example.bidmart.bidding.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.exception.InsufficientBalanceException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.common.event.BidPlacedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock
    private BidRepository bidRepository;

    @Mock
    private ListingLookupService listingLookupService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MockWalletService mockWalletService;
    private BidService bidService;

    @BeforeEach
    void setUp() {
        mockWalletService = new MockWalletService();
        mockWalletService.initializeDefaults();
        bidService = new BidService(bidRepository, listingLookupService, mockWalletService, eventPublisher);
    }

    @Test
    void placeBidSuccess() {
        UUID listingId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        ListingSnapshot listing = new ListingSnapshot(
                listingId,
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                LocalDateTime.now().plusHours(2),
                "ACTIVE"
        );

        when(listingLookupService.findById(listingId)).thenReturn(Optional.of(listing));
        when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId)).thenReturn(Optional.empty());
        when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, buyerId))
                .thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(UUID.randomUUID());
            bid.setCreatedAt(LocalDateTime.now());
            return bid;
        });

        mockWalletService.setAvailableBalance(buyerId, new BigDecimal("1000.00"));

        BidResponse response = bidService.placeBid(new CreateBidRequest(
                listingId,
                buyerId,
                new BigDecimal("150.00"),
                Boolean.FALSE,
                null
        ));

        assertEquals(listingId, response.listingId());
        assertEquals(buyerId, response.buyerId());
        assertEquals(new BigDecimal("150.00"), response.amount());
        assertEquals(new BigDecimal("850.00"), mockWalletService.getWalletState(buyerId).availableBalance());
        assertEquals(new BigDecimal("150.00"), mockWalletService.getWalletState(buyerId).lockedByListing().get(listingId));

        verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
    }

    @Test
    void placeBidFailedWhenAmountLowerThanCurrentHighest() {
        UUID listingId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        ListingSnapshot listing = new ListingSnapshot(
                listingId,
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                LocalDateTime.now().plusHours(2),
                "ACTIVE"
        );

        Bid highestBid = new Bid();
        highestBid.setListingId(listingId);
        highestBid.setBuyerId(UUID.randomUUID());
        highestBid.setAmount(new BigDecimal("200.00"));
        highestBid.setProxyBid(Boolean.FALSE);

        when(listingLookupService.findById(listingId)).thenReturn(Optional.of(listing));
        when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId)).thenReturn(Optional.of(highestBid));

        mockWalletService.setAvailableBalance(buyerId, new BigDecimal("1000.00"));

        assertThrows(BidValidationException.class, () -> bidService.placeBid(new CreateBidRequest(
                listingId,
                buyerId,
                new BigDecimal("150.00"),
                Boolean.FALSE,
                null
        )));

        verify(bidRepository, never()).save(any(Bid.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void placeBidFailedWhenBalanceInsufficient() {
        UUID listingId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        ListingSnapshot listing = new ListingSnapshot(
                listingId,
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                LocalDateTime.now().plusHours(2),
                "ACTIVE"
        );

        when(listingLookupService.findById(listingId)).thenReturn(Optional.of(listing));
        when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId)).thenReturn(Optional.empty());
        when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, buyerId))
                .thenReturn(Optional.empty());

        mockWalletService.setAvailableBalance(buyerId, new BigDecimal("50.00"));

        assertThrows(InsufficientBalanceException.class, () -> bidService.placeBid(new CreateBidRequest(
                listingId,
                buyerId,
                new BigDecimal("120.00"),
                Boolean.FALSE,
                null
        )));

        verify(bidRepository, never()).save(any(Bid.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void placeBidSuccessShouldReleasePreviousHighestBidderFunds() {
        UUID listingId = UUID.randomUUID();
        UUID previousBuyerId = UUID.randomUUID();
        UUID newBuyerId = UUID.randomUUID();

        ListingSnapshot listing = new ListingSnapshot(
                listingId,
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                LocalDateTime.now().plusHours(2),
                "ACTIVE"
        );

        Bid previousHighest = new Bid();
        previousHighest.setId(UUID.randomUUID());
        previousHighest.setListingId(listingId);
        previousHighest.setBuyerId(previousBuyerId);
        previousHighest.setAmount(new BigDecimal("200.00"));
        previousHighest.setProxyBid(Boolean.FALSE);
        previousHighest.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        when(listingLookupService.findById(listingId)).thenReturn(Optional.of(listing));
        when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId)).thenReturn(Optional.of(previousHighest));
        when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, newBuyerId))
                .thenReturn(Optional.empty());
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            bid.setId(UUID.randomUUID());
            bid.setCreatedAt(LocalDateTime.now());
            return bid;
        });

        mockWalletService.setAvailableBalance(previousBuyerId, new BigDecimal("500.00"));
        mockWalletService.reserveBidFunds(previousBuyerId, listingId, new BigDecimal("200.00"));

        mockWalletService.setAvailableBalance(newBuyerId, new BigDecimal("500.00"));

        bidService.placeBid(new CreateBidRequest(
                listingId,
                newBuyerId,
                new BigDecimal("250.00"),
                Boolean.FALSE,
                null
        ));

        assertEquals(new BigDecimal("500.00"), mockWalletService.getWalletState(previousBuyerId).availableBalance());
        assertTrue(mockWalletService.getWalletState(previousBuyerId).lockedByListing().isEmpty());
        assertEquals(new BigDecimal("250.00"), mockWalletService.getWalletState(newBuyerId).lockedByListing().get(listingId));

        verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
    }
}