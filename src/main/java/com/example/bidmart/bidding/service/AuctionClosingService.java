package com.example.bidmart.bidding.service;

import com.example.bidmart.bidding.model.Bid;
import com.example.bidmart.bidding.repository.BidRepository;
import com.example.bidmart.common.event.AuctionClosedNoWinnerEvent;
import com.example.bidmart.common.event.AuctionWonEvent;
import com.example.bidmart.listing.model.AuctionStatus;
import com.example.bidmart.listing.model.Listing;
import com.example.bidmart.listing.repository.ListingRepository;
import com.example.bidmart.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionClosingService {

    private final ListingRepository listingRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void closeAuction(Listing listing) {
        if (listing.getStatus().isFinal()) return;

        if (listing.isReservePriceMet() && listing.getCurrentHighestBidderId() != null) {
            listing.setStatus(AuctionStatus.WON);
            listingRepository.save(listing);

            walletService.settlePayment(
                    listing.getCurrentHighestBidderId(),
                    listing.getCurrentHighestBid(),
                    listing.getId().toString()
            );

            releaseLoserHolds(listing);

            eventPublisher.publishEvent(new AuctionWonEvent(
                    listing.getId(),
                    listing.getCurrentHighestBidderId(),
                    listing.getCurrentHighestBid()
            ));

        } else {
            listing.setStatus(AuctionStatus.UNSOLD);
            listingRepository.save(listing);

            releaseAllHolds(listing);

            eventPublisher.publishEvent(new AuctionClosedNoWinnerEvent(
                    listing.getId(),
                    listing.getCurrentHighestBid() == null ? "Tidak ada bid" : "Reserve price tidak tercapai"
            ));
        }
    }

    private void releaseLoserHolds(Listing listing) {
        UUID winnerId = listing.getCurrentHighestBidderId();
        Set<UUID> processed = new HashSet<>();

        for (Bid bid : bidRepository.findByListingIdOrderByCreatedAtDesc(listing.getId())) {
            UUID buyerId = bid.getBuyerId();
            if (buyerId.equals(winnerId) || !processed.add(buyerId)) continue;
            try {
                walletService.releaseBidFunds(buyerId, listing.getId(), bid.getReservedAmount());
            } catch (Exception e) {
                log.warn("Failed to release hold for buyer {}: {}", buyerId, e.getMessage());
            }
        }
    }

    private void releaseAllHolds(Listing listing) {
        Set<UUID> processed = new HashSet<>();

        for (Bid bid : bidRepository.findByListingIdOrderByCreatedAtDesc(listing.getId())) {
            UUID buyerId = bid.getBuyerId();
            if (!processed.add(buyerId)) continue;
            try {
                walletService.releaseBidFunds(buyerId, listing.getId(), bid.getReservedAmount());
            } catch (Exception e) {
                log.warn("Failed to release hold for buyer {}: {}", buyerId, e.getMessage());
            }
        }
    }
}
