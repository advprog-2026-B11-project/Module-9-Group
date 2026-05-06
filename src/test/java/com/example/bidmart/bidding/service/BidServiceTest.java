package com.example.bidmart.bidding.service;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.exception.ResourceNotFoundException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.bidding.validator.BidRuleValidator;
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

    // Mockito will use the single all-args constructor of BidService for injection.
    @Mock private BidRepository            bidRepository;
    @Mock private ListingService           listingService;
    @Mock private WalletService            walletService;
    @Mock private BidRuleValidator         bidRuleValidator;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BidService bidService;

    // =========================================================================
    // Shared test-data builders
    // =========================================================================

    /**
     * Returns a {@link Listing} whose auction is open (status=OPEN, endTime in the future).
     * The caller controls the IDs and the starting price.
     */
    private Listing activeListing(UUID id, UUID sellerId, BigDecimal startingPrice) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setSellerId(sellerId);
        listing.setStartingPrice(startingPrice);
        listing.setEndTime(LocalDateTime.now().plusHours(2));
        listing.setStatus("OPEN");
        return listing;
    }

    /**
     * Returns a persisted-style {@link Bid} (id and createdAt set) for a regular,
     * non-proxy bid.
     */
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

    /**
     * Returns a persisted-style proxy {@link Bid}.
     * {@code getReservedAmount()} returns {@code proxyMaxLimit} for this bid.
     */
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

    // =========================================================================
    // placeBid
    // =========================================================================

    @Nested
    class PlaceBid {

        /**
         * Golden-path test: no prior bids, single buyer, verifies the
         * end-to-end orchestration sequence using Mockito {@link InOrder}.
         *
         * Expected call order:
         *   1. bidRuleValidator.validateRequest
         *   2. listingService.getListingById
         *   3. bidRepository.findTopByListingIdOrderByAmountDescCreatedAtAsc
         *   4. bidRuleValidator.validateBidContext
         *   5. walletService.reserveBidFunds  (full amount, no prior reservation)
         *   6. bidRepository.save
         */
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

            // ── response content ──────────────────────────────────────────────
            assertThat(response.listingId()).isEqualTo(listingId);
            assertThat(response.buyerId()).isEqualTo(buyerId);
            assertThat(response.amount()).isEqualByComparingTo(amount);

            // ── strict call sequence ──────────────────────────────────────────
            InOrder seq = inOrder(bidRuleValidator, listingService, bidRepository, walletService);
            seq.verify(bidRuleValidator).validateRequest(request, buyerId);
            seq.verify(listingService).getListingById(listingId);
            seq.verify(bidRepository).findTopByListingIdOrderByAmountDescCreatedAtAsc(listingId);
            seq.verify(bidRuleValidator).validateBidContext(eq(buyerId), any(), eq(amount), any());
            seq.verify(walletService).reserveBidFunds(buyerId, listingId, amount);
            seq.verify(bidRepository).save(any(Bid.class));
        }

        /**
         * When {@code validateRequest} throws, the service must halt immediately:
         * no I/O calls (listing service, repository, wallet service) must occur.
         */
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
        }

        /**
         * When {@code validateBidContext} throws, the service must halt before
         * touching the wallet or persisting any bid.
         * (The listing and highest-bid fetches happen before Phase 2 validation
         * and are intentionally stubbed here to reach that code path.)
         */
        @Test
        void validateBidContextThrows_haltBeforeSaveAndWallet() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            UUID sellerId  = buyerId; // simulating a seller-tries-to-bid scenario
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

            // Phase 1 validator ran, but Phase 2 and all write operations must not run
            verify(bidRuleValidator).validateRequest(request, buyerId);
            verify(bidRuleValidator, never()).validateBidContext(any(), any(), any(), any());
            verify(walletService,   never()).reserveBidFunds(any(), any(), any());
            verify(bidRepository,   never()).save(any());
        }

        /**
         * When the same buyer raises their bid, only the incremental delta must
         * be sent to the wallet (not the full new amount).
         *
         * Setup:  buyer previously bid 200 → reservedAmount=200
         *         buyer now bids 300       → reserveTarget=300
         *         additionalReserve = max(300,200) - 200 = 100
         */
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

            // Only the delta (300 - 200 = 100) must be locked
            verify(walletService).reserveBidFunds(buyerId, listingId, new BigDecimal("100.00"));
            // Self-outbid: previous highest is same buyer → no release
            verify(walletService, never()).releaseBidFunds(any(), any(), any());
        }

        /**
         * When a new buyer outbids the current highest bidder, the previous
         * highest bidder's locked funds must be released.
         */
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
            // Previous bidder must have their full reserved amount returned
            verify(walletService).releaseBidFunds(
                    eq(previousBuyerId), eq(listingId), eq(previousAmount));
        }

        /**
         * For a proxy bid the reserve target is {@code proxyMaxLimit}, not
         * {@code amount}. The wallet must be charged the proxy ceiling.
         */
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

            // proxyMaxLimit (500) must be locked, not the bid amount (100)
            verify(walletService).reserveBidFunds(buyerId, listingId, proxyMaxLimit);
        }

        /**
         * Edge-case: buyer's previous proxy reservation already covers the new
         * regular-bid target, so the additional reserve delta is zero and
         * {@code reserveBidFunds} must NOT be called.
         *
         * Setup:  buyer's previous proxy had proxyMaxLimit=500 → reservedAmount=500
         *         new regular bid amount=300                   → reserveTarget=300
         *         additionalReserve = max(300,500) - 500 = 0
         */
        @Test
        void previousProxyReservationCoversNewBid_reserveFundsNotCalled() {
            UUID listingId = UUID.randomUUID();
            UUID buyerId   = UUID.randomUUID();
            BigDecimal newAmount = new BigDecimal("300.00");
            CreateBidRequest request = new CreateBidRequest(listingId, newAmount, false, null);

            // Previous bid was a proxy with maxLimit=500 → getReservedAmount() returns 500
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
        }
    }

    // =========================================================================
    // getBidsByListing
    // =========================================================================

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

    // =========================================================================
    // getHighestBid
    // =========================================================================

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

    // =========================================================================
    // getBidsByBuyer
    // =========================================================================

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
