package com.example.bidmart.listing.service;

import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.repository.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private ListingRepository listingRepository;

    @InjectMocks
    private ListingService listingService;

    private UUID listingId;
    private Listing listing;

    @BeforeEach
    void setUp() {
        listingId = UUID.randomUUID();

        listing = new Listing();
        listing.setId(listingId);
        listing.setSellerId(UUID.randomUUID());
        listing.setStartingPrice(new BigDecimal("100"));
        listing.setEndTime(LocalDateTime.now().plusHours(1));
        listing.setStatus("ACTIVE");
    }

    @Test
    void updateListing_shouldFail_whenAuctionActive() {
        when(listingRepository.findById(listingId))
                .thenReturn(Optional.of(listing));

        Listing updated = new Listing();

        assertThrows(RuntimeException.class, () -> {
            listingService.updateListing(listingId, updated);
        });
    }

    @Test
    void updateListing_shouldSuccess_whenAuctionNotActive() {
        listing.setStatus("CLOSED");

        when(listingRepository.findById(listingId))
                .thenReturn(Optional.of(listing));
        when(listingRepository.save(listing))
                .thenReturn(listing);

        Listing updated = new Listing();
        updated.setTitle("Updated");

        Listing result = listingService.updateListing(listingId, updated);

        assertEquals("Updated", result.getTitle());
    }

    @Test
    void deleteListing_shouldFail_whenAuctionActive() {
        when(listingRepository.findById(listingId))
                .thenReturn(Optional.of(listing));

        assertThrows(RuntimeException.class, () -> {
            listingService.deleteListing(listingId);
        });
    }

    @Test
    void deleteListing_shouldSuccess_whenAuctionNotActive() {
        listing.setStatus("CLOSED");

        when(listingRepository.findById(listingId))
                .thenReturn(Optional.of(listing));

        listingService.deleteListing(listingId);

        verify(listingRepository).delete(listing);
    }
}