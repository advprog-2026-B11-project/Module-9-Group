package com.example.bidmart.bidding.controller;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.service.BidService;
import com.example.bidmart.user.service.UserService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bids")
public class BidController {

    private final BidService bidService;
    private final UserService userService;

    public BidController(BidService bidService, UserService userService) {
        this.bidService = bidService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<BidResponse> placeBid(
            @RequestBody CreateBidRequest request,
            Authentication authentication
    ) {
        UUID buyerId = resolveCurrentUserId(authentication);
        BidResponse response = bidService.placeBid(buyerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/listing/{listingId}")
    public List<BidResponse> getBidsByListing(@PathVariable UUID listingId) {
        return bidService.getBidsByListing(listingId);
    }

    @GetMapping("/listing/{listingId}/highest")
    public BidResponse getHighestBid(@PathVariable UUID listingId) {
        return bidService.getHighestBid(listingId);
    }

    @GetMapping("/me")
    public List<BidResponse> getMyBids(Authentication authentication) {
        UUID buyerId = resolveCurrentUserId(authentication);
        return bidService.getBidsByBuyer(buyerId);
    }

    @GetMapping("/buyer/{buyerId}")
    public List<BidResponse> getBidsByBuyer(
            @PathVariable UUID buyerId,
            Authentication authentication
    ) {
        UUID authenticatedUserId = resolveCurrentUserId(authentication);
        if (!authenticatedUserId.equals(buyerId)) {
            throw new AccessDeniedException("Anda tidak dapat mengakses bid milik user lain.");
        }

        return bidService.getBidsByBuyer(buyerId);
    }

    private UUID resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("User belum terautentikasi.");
        }

        return userService.getUserIdByUsername(authentication.getName());
    }
}
