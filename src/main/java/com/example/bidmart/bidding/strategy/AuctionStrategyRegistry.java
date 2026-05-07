package com.example.bidmart.bidding.strategy;

import com.example.bidmart.bidding.exception.UnsupportedAuctionTypeException;
import com.example.bidmart.listing.model.AuctionType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AuctionStrategyRegistry {

    private final Map<AuctionType, AuctionStrategy> strategies;

    public AuctionStrategyRegistry(List<AuctionStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(AuctionStrategy::getSupportedType, s -> s));
    }

    public AuctionStrategy getStrategy(AuctionType type) {
        return Optional.ofNullable(strategies.get(type))
                .orElseThrow(() -> new UnsupportedAuctionTypeException(
                        "Tipe lelang tidak didukung: " + type));
    }
}
