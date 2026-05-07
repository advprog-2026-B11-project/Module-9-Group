package com.example.bidmart.bidding.strategy;

import com.example.bidmart.bidding.service.ListingSnapshot;
import com.example.bidmart.listing.model.AuctionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EnglishAuctionStrategyTest {

    private final EnglishAuctionStrategy strategy = new EnglishAuctionStrategy();

    private ListingSnapshot snapshot(BigDecimal startingPrice, BigDecimal currentHighestBid) {
        return new ListingSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                startingPrice,
                LocalDateTime.now().plusHours(1),
                AuctionStatus.ACTIVE,
                currentHighestBid,
                currentHighestBid == null ? null : UUID.randomUUID()
        );
    }

    @Test
    void validateBid_firstBid_belowStartingPrice_returnsFail() {
        ListingSnapshot listing = snapshot(new BigDecimal("100"), null);

        ValidationResult result = strategy.validateBid(new BigDecimal("50"), listing);

        assertThat(result.valid()).isFalse();
        assertThat(result.errorMessage()).isNotBlank();
    }

    @Test
    void validateBid_firstBid_atStartingPrice_returnsOk() {
        ListingSnapshot listing = snapshot(new BigDecimal("100"), null);

        ValidationResult result = strategy.validateBid(new BigDecimal("100"), listing);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validateBid_existingBids_atCurrentHighest_returnsFail() {
        ListingSnapshot listing = snapshot(new BigDecimal("100"), new BigDecimal("200"));

        ValidationResult result = strategy.validateBid(new BigDecimal("200"), listing);

        assertThat(result.valid()).isFalse();
    }

    @Test
    void validateBid_existingBids_aboveCurrentHighest_returnsOk() {
        ListingSnapshot listing = snapshot(new BigDecimal("100"), new BigDecimal("200"));

        ValidationResult result = strategy.validateBid(new BigDecimal("201"), listing);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void computeMinimumNextBid_noBids_returnsStartingPrice() {
        ListingSnapshot listing = snapshot(new BigDecimal("100"), null);

        BigDecimal min = strategy.computeMinimumNextBid(listing);

        assertThat(min).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void computeMinimumNextBid_withCurrentBid_returnsHighestPlusOne() {
        ListingSnapshot listing = snapshot(new BigDecimal("100"), new BigDecimal("250"));

        BigDecimal min = strategy.computeMinimumNextBid(listing);

        assertThat(min).isEqualByComparingTo(new BigDecimal("251"));
    }
}
