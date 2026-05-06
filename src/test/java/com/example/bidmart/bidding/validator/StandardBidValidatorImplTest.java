package com.example.bidmart.bidding.validator;

import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.exception.BidValidationException;
import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.service.ListingSnapshot;
import com.example.bidmart.listing.model.AuctionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StandardBidValidatorImplTest {

    private StandardBidValidatorImpl validator;

    private static final UUID LISTING_ID = UUID.randomUUID();
    private static final UUID BUYER_ID   = UUID.randomUUID();
    private static final UUID SELLER_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = new StandardBidValidatorImpl();
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    private CreateBidRequest validRequest() {
        return new CreateBidRequest(LISTING_ID, new BigDecimal("100.00"), false, null);
    }

    private CreateBidRequest proxyRequest(BigDecimal amount, BigDecimal proxyMaxLimit) {
        return new CreateBidRequest(LISTING_ID, amount, true, proxyMaxLimit);
    }

    /** An "open" listing: status=ACTIVE, endTime 2 hours in the future. */
    private ListingSnapshot openListing() {
        return new ListingSnapshot(
                LISTING_ID,
                SELLER_ID,
                new BigDecimal("50.00"),
                LocalDateTime.now().plusHours(2),
                AuctionStatus.ACTIVE,
                null,
                null
        );
    }

    private Bid bidWithAmount(BigDecimal amount) {
        Bid bid = new Bid();
        bid.setId(UUID.randomUUID());
        bid.setListingId(LISTING_ID);
        bid.setBuyerId(UUID.randomUUID());
        bid.setAmount(amount);
        bid.setProxyBid(false);
        return bid;
    }

    // =========================================================================
    // Task 1A: validateRequest
    // =========================================================================

    @Nested
    class ValidateRequest {

        @Test
        void nullRequest_throws() {
            assertThatThrownBy(() -> validator.validateRequest(null, BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Request bid tidak boleh kosong.");
        }

        @Test
        void nullListingId_throws() {
            CreateBidRequest req = new CreateBidRequest(null, new BigDecimal("100.00"), false, null);

            assertThatThrownBy(() -> validator.validateRequest(req, BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("listingId wajib diisi.");
        }

        @Test
        void nullBuyerId_throws() {
            assertThatThrownBy(() -> validator.validateRequest(validRequest(), null))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("buyerId wajib diisi.");
        }

        @Test
        void nullAmount_throws() {
            CreateBidRequest req = new CreateBidRequest(LISTING_ID, null, false, null);

            assertThatThrownBy(() -> validator.validateRequest(req, BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Amount wajib lebih dari 0.");
        }

        @Test
        void zeroAmount_throws() {
            CreateBidRequest req = new CreateBidRequest(LISTING_ID, BigDecimal.ZERO, false, null);

            assertThatThrownBy(() -> validator.validateRequest(req, BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Amount wajib lebih dari 0.");
        }

        @Test
        void negativeAmount_throws() {
            CreateBidRequest req = new CreateBidRequest(LISTING_ID, new BigDecimal("-0.01"), false, null);

            assertThatThrownBy(() -> validator.validateRequest(req, BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Amount wajib lebih dari 0.");
        }

        @Test
        void proxyBid_nullProxyMaxLimit_throws() {
            assertThatThrownBy(() -> validator.validateRequest(proxyRequest(new BigDecimal("100.00"), null), BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("proxyMaxLimit wajib diisi dan lebih dari 0");
        }

        @Test
        void proxyBid_zeroProxyMaxLimit_throws() {
            assertThatThrownBy(() -> validator.validateRequest(proxyRequest(new BigDecimal("100.00"), BigDecimal.ZERO), BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("proxyMaxLimit wajib diisi dan lebih dari 0");
        }

        @Test
        void proxyBid_negativeProxyMaxLimit_throws() {
            assertThatThrownBy(() -> validator.validateRequest(proxyRequest(new BigDecimal("100.00"), new BigDecimal("-50.00")), BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("proxyMaxLimit wajib diisi dan lebih dari 0");
        }

        @Test
        void proxyBid_proxyMaxLimitLessThanAmount_throws() {
            assertThatThrownBy(() -> validator.validateRequest(proxyRequest(new BigDecimal("100.00"), new BigDecimal("80.00")), BUYER_ID))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("proxyMaxLimit tidak boleh lebih kecil dari amount.");
        }

        @Test
        void proxyBid_proxyMaxLimitExactlyEqualToAmount_passes() {
            assertThatNoException()
                    .isThrownBy(() -> validator.validateRequest(
                            proxyRequest(new BigDecimal("100.00"), new BigDecimal("100.00")), BUYER_ID));
        }

        @Test
        void proxyBid_proxyMaxLimitGreaterThanAmount_passes() {
            assertThatNoException()
                    .isThrownBy(() -> validator.validateRequest(
                            proxyRequest(new BigDecimal("100.00"), new BigDecimal("500.00")), BUYER_ID));
        }

        @Test
        void normalBid_allFieldsValid_passes() {
            assertThatNoException()
                    .isThrownBy(() -> validator.validateRequest(validRequest(), BUYER_ID));
        }

        @Test
        void proxyBidFalse_proxyMaxLimitIgnored_passes() {
            CreateBidRequest req = new CreateBidRequest(LISTING_ID, new BigDecimal("100.00"), false, new BigDecimal("50.00"));

            assertThatNoException()
                    .isThrownBy(() -> validator.validateRequest(req, BUYER_ID));
        }
    }

    // =========================================================================
    // Task 1B: validateBidContext
    // =========================================================================

    @Nested
    class ValidateBidContext {

        @Test
        void buyerIsSeller_throws() {
            ListingSnapshot listing = new ListingSnapshot(
                    LISTING_ID, BUYER_ID, new BigDecimal("50.00"),
                    LocalDateTime.now().plusHours(2), AuctionStatus.ACTIVE, null, null
            );

            assertThatThrownBy(() -> validator.validateBidContext(
                    BUYER_ID, listing, new BigDecimal("100.00"), Optional.empty()))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Seller tidak boleh melakukan bid pada listing miliknya sendiri.");
        }

        @Test
        void nullSellerIdOnListing_noConflictCheck_passes() {
            ListingSnapshot listing = new ListingSnapshot(
                    LISTING_ID, null, new BigDecimal("50.00"),
                    LocalDateTime.now().plusHours(2), AuctionStatus.ACTIVE, null, null
            );

            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, listing, new BigDecimal("100.00"), Optional.empty()));
        }

        @Test
        void buyerIsNotSeller_passes() {
            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, openListing(), new BigDecimal("100.00"), Optional.empty()));
        }

        @Test
        void auctionExpiredByEndTime_throws() {
            ListingSnapshot listing = new ListingSnapshot(
                    LISTING_ID, SELLER_ID, new BigDecimal("50.00"),
                    LocalDateTime.now().minusSeconds(1),
                    AuctionStatus.ACTIVE, null, null
            );

            assertThatThrownBy(() -> validator.validateBidContext(
                    BUYER_ID, listing, new BigDecimal("100.00"), Optional.empty()))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("waktu lelang telah berakhir");
        }

        @Test
        void auctionStatusClosed_throws() {
            ListingSnapshot listing = new ListingSnapshot(
                    LISTING_ID, SELLER_ID, new BigDecimal("50.00"),
                    LocalDateTime.now().plusHours(2),
                    AuctionStatus.CLOSED, null, null
            );

            assertThatThrownBy(() -> validator.validateBidContext(
                    BUYER_ID, listing, new BigDecimal("100.00"), Optional.empty()))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Auction sudah ditutup");
        }

        @Test
        void auctionStatusActive_passes() {
            ListingSnapshot listing = new ListingSnapshot(
                    LISTING_ID, SELLER_ID, new BigDecimal("50.00"),
                    LocalDateTime.now().plusHours(2), AuctionStatus.ACTIVE, null, null
            );

            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, listing, new BigDecimal("100.00"), Optional.empty()));
        }

        @Test
        void auctionStatusExtended_passes() {
            ListingSnapshot listing = new ListingSnapshot(
                    LISTING_ID, SELLER_ID, new BigDecimal("50.00"),
                    LocalDateTime.now().plusMinutes(1), AuctionStatus.EXTENDED, null, null
            );

            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, listing, new BigDecimal("100.00"), Optional.empty()));
        }

        @Test
        void bidBelowStartingPrice_throws() {
            assertThatThrownBy(() -> validator.validateBidContext(
                    BUYER_ID, openListing(), new BigDecimal("30.00"), Optional.empty()))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("lebih besar atau sama dengan starting price");
        }

        @Test
        void bidExactlyAtStartingPrice_noCurrentHighest_passes() {
            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, openListing(), new BigDecimal("50.00"), Optional.empty()));
        }

        @Test
        void nullStartingPrice_treatedAsZero_smallPositiveBid_passes() {
            ListingSnapshot listing = new ListingSnapshot(
                    LISTING_ID, SELLER_ID, null,
                    LocalDateTime.now().plusHours(2), AuctionStatus.ACTIVE, null, null
            );

            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, listing, new BigDecimal("0.01"), Optional.empty()));
        }

        @Test
        void bidEqualsCurrentHighestAmount_throws() {
            Bid currentHighest = bidWithAmount(new BigDecimal("100.00"));

            assertThatThrownBy(() -> validator.validateBidContext(
                    BUYER_ID, openListing(), new BigDecimal("100.00"), Optional.of(currentHighest)))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Bid harus lebih tinggi dari bid tertinggi saat ini.");
        }

        @Test
        void bidBelowCurrentHighestAmount_throws() {
            Bid currentHighest = bidWithAmount(new BigDecimal("200.00"));

            assertThatThrownBy(() -> validator.validateBidContext(
                    BUYER_ID, openListing(), new BigDecimal("150.00"), Optional.of(currentHighest)))
                    .isInstanceOf(BidValidationException.class)
                    .hasMessageContaining("Bid harus lebih tinggi dari bid tertinggi saat ini.");
        }

        @Test
        void bidAboveCurrentHighestAmount_passes() {
            Bid currentHighest = bidWithAmount(new BigDecimal("100.00"));

            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, openListing(), new BigDecimal("101.00"), Optional.of(currentHighest)));
        }

        @Test
        void noCurrentHighestBid_bidAboveStartingPrice_passes() {
            assertThatNoException()
                    .isThrownBy(() -> validator.validateBidContext(
                            BUYER_ID, openListing(), new BigDecimal("999.00"), Optional.empty()));
        }
    }
}
