package com.example.bidmart.listing.service;

import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.repository.ListingRepository;
import com.example.bidmart.bidding.service.ListingSnapshot;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ListingService {

    private final ListingRepository listingRepository;

    public ListingService(ListingRepository listingRepository) {
        this.listingRepository = listingRepository;
    }

    public Listing createListing(Listing listing, UUID sellerId) {
        listing.setSellerId(sellerId);
        listing.setCreatedAt(LocalDateTime.now());

        if (listing.getStatus() == null || listing.getStatus().isBlank()) {
            listing.setStatus("ACTIVE");
        }

        return listingRepository.save(listing);
    }

    public List<Listing> getAllListings() {
        return listingRepository.findAll();
    }

    public Optional<Listing> getListingById(UUID id) {
        return listingRepository.findById(id);
    }

    public Listing updateListing(UUID id, Listing updatedListing) {
        Listing existing = listingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Listing tidak ditemukan dengan ID: " + id));

        validateListingForUpdate(existing);

        existing.setTitle(updatedListing.getTitle());
        existing.setDescription(updatedListing.getDescription());
        existing.setImageUrl(updatedListing.getImageUrl());
        existing.setStartingPrice(updatedListing.getStartingPrice());
        existing.setReservePrice(updatedListing.getReservePrice());
        existing.setEndTime(updatedListing.getEndTime());
        existing.setStatus(updatedListing.getStatus());

        return listingRepository.save(existing);
    }

    private void validateListingForUpdate(Listing listing) {
        ListingSnapshot snapshot = new ListingSnapshot(
                listing.getId(),
                listing.getSellerId(),
                listing.getStartingPrice(),
                listing.getEndTime(),
                listing.getStatus()
        );

        if (snapshot.isOpenAt(LocalDateTime.now())) {
            throw new RuntimeException("Listing tidak bisa diupdate saat auction masih aktif.");
        }
    }

    public void deleteListing(UUID id) {
        Listing existing = listingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Listing tidak ditemukan dengan ID: " + id));

        validateListingForUpdate(existing);

        listingRepository.delete(existing);
    }
}