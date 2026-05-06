package com.example.bidmart.bidding.service;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.event.OutbidEvent;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.exception.ResourceNotFoundException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.bidding.validator.BidRuleValidator;
import com.example.bidmart.common.event.BidPlacedEvent;
import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.service.ListingService;
import com.example.bidmart.wallet.service.WalletService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BidServiceTest {

    @Mock private BidRepository            bidRepository;
    @Mock private ListingService           listingService;
    @Mock private WalletService            walletService;
    @Mock private BidRuleValidator         bidRuleValidator;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BidService bidService;

    private Listing activeListing(UUID id, UUID sellerId, BigDecimal startingPrice) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setStartingPrice(startingPrice);
        listing.setEndTime(LocalDateTime.now().plusHours(2));
        listing.setStatus("OPEN");
        return listing;
    }

    private Bid regularBid(UUID listingId, UUID buyerId, BigDecimal amount) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setListingId(listingId);
        bid.setBuyerId(buyerId);
        bid.setAmount(amount);
        bid.setProxyBid(false);
        bid.setCreatedAt(LocalDateTime.now());
        return bid;
    }

    private Bid proxyBid(UUID listingId, UUID buyerId, BigDecimal amount, BigDecimal proxyMaxLimit) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setListingId(listingId);
        bid.setBuyerId(buyerId);
        bid.setAmount(amount);
        bid.setProxyBid(true);
        bid.setProxyMaxLimit(proxyMaxLimit);
        bid.setCreatedAt(LocalDateTime.now());
        return bid;
    }

    @Nested
    class PlaceBid {

        @Test
        void firstBid_verifyFullOrchestratorSequence() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            UUID sellerId  = UUID.randomUUID();
            BigDecimal amount = new BigDecimal("150.00");
            CreateBidRequest request = new CreateBidRequest(listingId, amount, false, null);

            when(listingService.getListingById(listingId))
                    .thenReturn(Optional.of(activeListing(listingId, sellerId, new BigDecimal("100.00"))));
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.empty());
            when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, buyerId))
                    .thenReturn(Optional.empty());
            when(bidRepository.save(any(Bid.class)))
                    .thenAnswer(inv -> regularBid(listingId, buyerId, amount));

            BidResponse response = bidService.placeBid(buyerId, request);

            assertThat(response.listingId()).isEqualTo(listingId);
            assertThat(response.buyerId()).isEqualTo(buyerId);
            assertThat(response.amount()).isEqualByComparingTo(amount);

            InOrder seq = inOrder(bidRuleValidator, listingService, bidRepository, walletService);
            seq.verify(bidRuleValidator).validateRequest(request, buyerId);
            seq.verify(listingService).getListingById(listingId);
            seq.verify(bidRepository).findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId);
            seq.verify(bidRuleValidator).validateBidContext(eq(buyerId), any(), eq(amount), any());
            seq.verify(walletService).reserveBidFunds(buyerId, listingId, amount);
            seq.verify(bidRepository).save(any(Bid.class));

            verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
        }

        @Test
        void validateRequestThrows_noIOCallsMade() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            CreateBidRequest request = new CreateBidRequest(listingId, null, false, null);

            doThrow(new BidValidationException("Amount wajib lebih dari 0."))
                    .when(bidRuleValidator).validateRequest(request, buyerId);

            assertThatThrownBy(() -> bidService.placeBid(buyerId, request))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Amount wajib lebih dari 0.");

            verify(listingService, never()).getListingById(any());
            verify(bidRepository, never()).findTopByListingIdOrderByAmountDescCreatedAtAsc(any());
            verify(bidRuleValidator, never()).validateBidContext(any(), any(), any(), any());
            verify(walletService,   never()).reserveBidFunds(any(), any(), any());
            verify(bidRepository,   never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void validateBidContextThrows_haltBeforeSaveAndWallet() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            UUID sellerId  = buyerId;
            CreateBidRequest request = new CreateBidRequest(listingId, new BigDecimal("100.00"), false, null);

            when(listingService.getListingById(listingId))
                    .thenReturn(Optional.of(activeListing(listingId, sellerId, new BigDecimal("50.00"))));
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.empty());
            doThrow(new BidValidationException("Seller tidak boleh melakukan bid pada listing miliknya sendiri."))
                    .when(bidRuleValidator).validateBidContext(any(), any(), any(), any());

            assertThatThrownBy(() -> bidService.placeBid(buyerId, request))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Seller tidak boleh melakukan bid");

            verify(walletService, never()).reserveBidFunds(any(), any(), any());
            verify(bidRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void listingNotFound_throwsResourceNotFoundExceptionWithListingId() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            CreateBidRequest request = new CreateBidRequest(listingId, new BigDecimal("100.00"), false, null);

            when(listingService.getListingById(listingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> bidService.placeBid(buyerId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(listingId.toString());

            verify(bidRuleValidator).validateRequest(request, buyerId);
            verify(bidRuleValidator, never()).validateBidContext(any(), any(), any(), any());
            verify(walletService,   never()).reserveBidFunds(any(), any(), any());
            verify(bidRepository,   never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void sameBuyerRaisesBid_onlyDeltaReserved_noFundsReleased() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            BigDecimal previousAmount = new BigDecimal("200.00");
            BigDecimal newAmount      = new BigDecimal("300.00");
            CreateBidRequest request  = new CreateBidRequest(listingId, newAmount, false, null);

            Bid previousByBuyer = regularBid(listingId, buyerId, previousAmount);

            when(listingService.getListingById(listingId))
                    .thenReturn(Optional.of(activeListing(listingId, UUID.randomUUID(), new BigDecimal("100.00"))));
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.of(previousByBuyer));
            when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, buyerId))
                    .thenReturn(Optional.of(previousByBuyer));
            when(bidRepository.save(any(Bid.class)))
                    .thenAnswer(inv -> regularBid(listingId, buyerId, newAmount));

            bidService.placeBid(buyerId, request);

            verify(walletService).reserveBidFunds(buyerId, listingId, new BigDecimal("100.00"));
            verify(walletService, never()).releaseBidFunds(any(), any(), any());
            verify(eventPublisher, never()).publishEvent(any(OutbidEvent.class));
            verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
        }

        @Test
        void outbidsDifferentBuyer_releasesPreviousHighestBidderFunds() {
            UUID listingId      = UUID.randomUUID();
            UUID previousBuyerId = UUID.randomUUID();
            UUID newBuyerId      = UUID.randomUUID();
            BigDecimal previousAmount = new BigDecimal("200.00");
            BigDecimal newAmount      = new BigDecimal("250.00");
            CreateBidRequest request  = new CreateBidRequest(listingId, newAmount, false, null);

            Bid previousHighest = regularBid(listingId, previousBuyerId, previousAmount);

            when(listingService.getListingById(listingId))
                    .thenReturn(Optional.of(activeListing(listingId, UUID.randomUUID(), new BigDecimal("100.00"))));
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.of(previousHighest));
            when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, newBuyerId))
                    .thenReturn(Optional.empty());
            when(bidRepository.save(any(Bid.class)))
                    .thenAnswer(inv -> regularBid(listingId, newBuyerId, newAmount));

            bidService.placeBid(newBuyerId, request);

            verify(walletService).reserveBidFunds(newBuyerId, listingId, newAmount);
            verify(walletService).releaseBidFunds(eq(previousBuyerId), eq(listingId), eq(previousAmount));

            verify(eventPublisher).publishEvent(any(OutbidEvent.class));
            verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
        }

        @Test
        void proxyBid_proxyMaxLimitUsedAsReserveTarget() {
            UUID listingId     = UUID.randomUUID();
            UUID buyerId       = UUID.randomUUID();
            BigDecimal amount        = new BigDecimal("100.00");
            BigDecimal proxyMaxLimit = new BigDecimal("500.00");
            CreateBidRequest request = new CreateBidRequest(listingId, amount, true, proxyMaxLimit);

            when(listingService.getListingById(listingId))
                    .thenReturn(Optional.of(activeListing(listingId, UUID.randomUUID(), new BigDecimal("50.00"))));
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.empty());
            when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, buyerId))
                    .thenReturn(Optional.empty());
            when(bidRepository.save(any(Bid.class)))
                    .thenAnswer(inv -> {
                        Bid b = inv.getArgument(0);
                        b.setId(UUID.randomUUID());
                        b.setCreatedAt(LocalDateTime.now());
                        return b;
                    });

            bidService.placeBid(buyerId, request);

            verify(walletService).reserveBidFunds(buyerId, listingId, proxyMaxLimit);
            verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
        }

        @Test
        void previousProxyReservationCoversNewBid_reserveFundsNotCalled() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            BigDecimal newAmount = new BigDecimal("300.00");
            CreateBidRequest request = new CreateBidRequest(listingId, newAmount, false, null);

            Bid previousProxyBid = proxyBid(listingId, buyerId, new BigDecimal("200.00"), new BigDecimal("500.00"));

            when(listingService.getListingById(listingId))
                    .thenReturn(Optional.of(activeListing(listingId, UUID.randomUUID(), new BigDecimal("100.00"))));
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.of(previousProxyBid));
            when(bidRepository.findTopByListingIdAndBuyerIdOrderByCreatedAtDesc(listingId, buyerId))
                    .thenReturn(Optional.of(previousProxyBid));
            when(bidRepository.save(any(Bid.class)))
                    .thenAnswer(inv -> regularBid(listingId, buyerId, newAmount));

            bidService.placeBid(buyerId, request);

            verify(walletService, never()).reserveBidFunds(any(), any(), any());
            verify(eventPublisher).publishEvent(any(BidPlacedEvent.class));
        }
    }

    @Nested
    class GetBidsByListing {

        @Test
        void nullListingId_throws() {
            assertThatThrownBy(() -> bidService.getBidsByListing(null))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("listingId wajib diisi.");
        }

        @Test
        void validListingId_returnsAllBidsForListing() {
            UUID listingId = UUID.randomUUID();
            Bid bid = regularBid(listingId, UUID.randomUUID(), new BigDecimal("100.00"));
            when(bidRepository.findByListingIdOrderByCreatedAtDesc(listingId))
                    .thenReturn(List.of(bid));

            List<BidResponse> result = bidService.getBidsByListing(listingId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).listingId()).isEqualTo(listingId);
        }
    }

    @Nested
    class GetHighestBid {

        @Test
        void nullListingId_throws() {
            assertThatThrownBy(() -> bidService.getHighestBid(null))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("listingId wajib diisi.");
        }

        @Test
        void noBidsExist_throwsResourceNotFoundExceptionWithListingId() {
            UUID listingId = UUID.randomUUID();
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bidService.getHighestBid(listingId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(listingId.toString());
        }

        @Test
        void bidsExist_returnsHighestBid() {
            UUID listingId = UUID.randomUUID();
            Bid highest = regularBid(listingId, UUID.randomUUID(), new BigDecimal("500.00"));
            when(bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId))
                    .thenReturn(Optional.of(highest));

            BidResponse result = bidService.getHighestBid(listingId);

            assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("500.00"));
        }
    }

    @Nested
    class GetBidsByBuyer {

        @Test
        void nullBuyerId_throws() {
            assertThatThrownBy(() -> bidService.getBidsByBuyer(null))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("buyerId wajib diisi.");
        }

        @Test
        void validBuyerId_returnsAllBidsForBuyer() {
            UUID buyerId   = UUID.randomUUID();
            UUID listingId = UUID.randomUUID();
            Bid bid = regularBid(listingId, buyerId, new BigDecimal("200.00"));
            when(bidRepository.findByBuyerIdOrderByCreatedAtDesc(buyerId))
                    .thenReturn(List.of(bid));

            List<BidResponse> result = bidService.getBidsByBuyer(buyerId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).buyerId()).isEqualTo(buyerId);
        }
    }
}