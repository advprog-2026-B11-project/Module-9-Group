package com.example.bidmart.bidding.scheduler;

import com.example.bidmart.bidding.service.AuctionClosingService;
import com.example.bidmart.listing.model.AuctionStatus;
import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.repository.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionExpirySchedulerTest {

    @Mock private AuctionClosingService auctionClosingService;
    @Mock private ListingRepository     listingRepository;

    @InjectMocks
    private AuctionExpiryScheduler scheduler;

    private Listing expiredListing() {
        Listing listing = new Listing();
        listing.setId(UUID.randomUUID());
        listing.setStatus(AuctionStatus.ACTIVE);
        listing.setEndTime(LocalDateTime.now().minusMinutes(5));
        return listing;
    }

    @Test
    void closeExpiredAuctions_foundExpired_closesAll() {
        Listing l1 = expiredListing();
        Listing l2 = expiredListing();

        when(listingRepository.findByStatusInAndEndTimeBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(l1, l2));

        scheduler.closeExpiredAuctions();

        verify(auctionClosingService).closeAuction(l1);
        verify(auctionClosingService).closeAuction(l2);
    }

    @Test
    void closeExpiredAuctions_oneThrows_continuesWithOthers() {
        Listing l1 = expiredListing();
        Listing l2 = expiredListing();

        when(listingRepository.findByStatusInAndEndTimeBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of(l1, l2));
        doThrow(new RuntimeException("DB error"))
                .when(auctionClosingService).closeAuction(l1);

        scheduler.closeExpiredAuctions();

        verify(auctionClosingService).closeAuction(l1);
        verify(auctionClosingService).closeAuction(l2);
    }

    @Test
    void closeExpiredAuctions_noneExpired_doesNothing() {
        when(listingRepository.findByStatusInAndEndTimeBefore(anyList(), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.closeExpiredAuctions();

        verify(auctionClosingService, never()).closeAuction(any());
    }
}
