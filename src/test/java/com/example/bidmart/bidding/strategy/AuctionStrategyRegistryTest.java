package com.example.bidmart.bidding.strategy;

import com.example.bidmart.bidding.exception.UnsupportedAuctionTypeException;
import com.example.bidmart.listing.model.AuctionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuctionStrategyRegistryTest {

    private final AuctionStrategyRegistry registry =
            new AuctionStrategyRegistry(List.of(new EnglishAuctionStrategy()));

    @Test
    void getStrategy_english_returnsEnglishStrategy() {
        AuctionStrategy strategy = registry.getStrategy(AuctionType.ENGLISH);

        assertThat(strategy).isInstanceOf(EnglishAuctionStrategy.class);
    }

    @Test
    void getStrategy_unknownType_throwsUnsupportedAuctionTypeException() {
        assertThatThrownBy(() -> registry.getStrategy(null))
                .isInstanceOf(UnsupportedAuctionTypeException.class);
    }
}
