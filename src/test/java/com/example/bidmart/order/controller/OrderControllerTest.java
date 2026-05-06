package com.example.bidmart.order.controller;

import com.example.bidmart.order.model.Order;
import com.example.bidmart.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @Test
    void getOrdersByBuyer_returnsOk() {
        UUID buyerId = UUID.randomUUID();
        Order order = new Order();
        when(orderService.getOrdersByBuyer(buyerId)).thenReturn(Arrays.asList(order));

        ResponseEntity<List<Order>> response = orderController.getOrdersByBuyer(buyerId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void updateOrderStatus_returnsOk() {
        UUID orderId = UUID.randomUUID();
        Map<String, String> request = Map.of("status", "PAID");
        Order order = new Order();
        order.setStatus("PAID");

        when(orderService.updateOrderStatus(orderId, "PAID")).thenReturn(order);

        ResponseEntity<Order> response = orderController.updateOrderStatus(orderId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("PAID", response.getBody().getStatus());
    }

    @Test
    void updateTrackingNumber_returnsOk() {
        UUID orderId = UUID.randomUUID();
        Map<String, String> request = Map.of("trackingNumber", "RESI-123");
        Order order = new Order();
        order.setTrackingNumber("RESI-123");
        order.setStatus("SHIPPED");

        when(orderService.updateTrackingNumber(orderId, "RESI-123")).thenReturn(order);

        ResponseEntity<Order> response = orderController.updateTrackingNumber(orderId, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("RESI-123", response.getBody().getTrackingNumber());
        assertEquals("SHIPPED", response.getBody().getStatus());
    }

    @Test
    void createOrderTest_returnsOk() {
        UUID lId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        Map<String, String> request = Map.of("listingId", lId.toString(), "buyerId", bId.toString());

        when(orderService.createOrderAutomatically(lId, bId)).thenReturn(new Order());

        ResponseEntity<Order> response = orderController.createOrderTest(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deleteOrder_returnsOk() {
        UUID orderId = UUID.randomUUID();
        doNothing().when(orderService).deleteOrder(orderId);

        ResponseEntity<Map<String, String>> response = orderController.deleteOrder(orderId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Pesanan berhasil dihapus", response.getBody().get("message"));
    }
}