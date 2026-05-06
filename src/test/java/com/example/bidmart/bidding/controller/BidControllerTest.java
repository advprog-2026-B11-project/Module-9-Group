package com.example.bidmart.bidding.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.bidmart.bidding.dto.BidResponse;
import com.example.bidmart.bidding.dto.CreateBidRequest;
import com.example.bidmart.bidding.service.BidService;
import com.example.bidmart.user.service.UserService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class BidControllerTest {

    @Mock
    private BidService bidService;

    @Mock
    private UserService userService;

    @InjectMocks
    private BidController bidController;

    @Test
    void placeBidShouldReturnCreated() {
        UUID bidId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("buyerUser");
        when(userService.getUserIdByUsername("buyerUser")).thenReturn(buyerId);

        CreateBidRequest request = new CreateBidRequest(listingId, new BigDecimal("250.00"), Boolean.FALSE, null);
        BidResponse responsePayload = new BidResponse(
                bidId,
                listingId,
                buyerId,
                new BigDecimal("250.00"),
                Boolean.FALSE,
                null,
                LocalDateTime.now()
        );

        when(bidService.placeBid(buyerId, request)).thenReturn(responsePayload);

        ResponseEntity<BidResponse> response = bidController.placeBid(request, authentication);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(bidId, response.getBody().id());
    }

    @Test
    void getBidsByListingShouldReturnBidHistory() {
        UUID listingId = UUID.randomUUID();

        List<BidResponse> bids = List.of(new BidResponse(
                UUID.randomUUID(),
                listingId,
                UUID.randomUUID(),
                new BigDecimal("300.00"),
                Boolean.FALSE,
                null,
                LocalDateTime.now()
        ));

        when(bidService.getBidsByListing(listingId)).thenReturn(bids);

        List<BidResponse> result = bidController.getBidsByListing(listingId);

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("300.00"), result.get(0).amount());
    }
}
