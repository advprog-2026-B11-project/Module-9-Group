package com.example.bidmart.bidding.scheduler;

import com.example.bidmart.bidding.service.AuctionClosingService;
import com.example.bidmart.listing.model.AuctionStatus;
import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionExpiryScheduler {

    private final AuctionClosingService auctionClosingService;
    private final ListingRepository listingRepository;

    @Scheduled(fixedDelay = 30_000)
    public void closeExpiredAuctions() {
        List<Listing> expired = listingRepository.findByStatusInAndEndTimeBefore(
                List.of(AuctionStatus.ACTIVE, AuctionStatus.EXTENDED),
                LocalDateTime.now()
        );

        log.info("Found {} expired auctions to close", expired.size());

        int closedCount = 0;
        for (Listing listing : expired) {
            try {
                auctionClosingService.closeAuction(listing);
                closedCount++;
            } catch (Exception e) {
                log.error("Failed to close auction for listing {}: {}", listing.getId(), e.getMessage(), e);
            }
        }

        log.info("Closed {} auctions", closedCount);
    }
}
