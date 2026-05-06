package com.example.bidmart.order.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    void testOrderConstructorsGettersAndSetters() {
        Order emptyOrder = new Order();
        assertNotNull(emptyOrder);

        UUID listingId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        Order order = new Order(listingId, buyerId, "CREATED");

        assertEquals(listingId, order.getListingId());
        assertEquals(buyerId, order.getBuyerId());
        assertEquals("CREATED", order.getStatus());

        UUID newId = UUID.randomUUID();
        UUID newListingId = UUID.randomUUID();
        UUID newBuyerId = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.now();

        order.setId(newId);
        order.setListingId(newListingId);
        order.setBuyerId(newBuyerId);
        order.setStatus("PAID");
        order.setTrackingNumber("RESI-12345");
        order.setCreatedAt(time);

        assertEquals(newId, order.getId());
        assertEquals(newListingId, order.getListingId());
        assertEquals(newBuyerId, order.getBuyerId());
        assertEquals("PAID", order.getStatus());
        assertEquals("RESI-12345", order.getTrackingNumber());
        assertEquals(time, order.getCreatedAt());
    }

    @Test
    void testOrderBuilder() {
        UUID id = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.now();

        Order order = Order.builder()
                .id(id)
                .listingId(listingId)
                .buyerId(buyerId)
                .status("COMPLETED")
                .trackingNumber("RESI-999")
                .createdAt(time)
                .build();

        assertEquals(id, order.getId());
        assertEquals("COMPLETED", order.getStatus());
        assertEquals("RESI-999", order.getTrackingNumber());
    }

    @Test
    void testPrePersistOnCreate() {
        Order order = new Order();

        assertNull(order.getCreatedAt());

        order.onCreate();

        assertNotNull(order.getCreatedAt());
    }
}