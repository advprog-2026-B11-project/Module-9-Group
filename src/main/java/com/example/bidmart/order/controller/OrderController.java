package com.example.bidmart.order.controller;

import com.example.bidmart.order.model.Order;
import com.example.bidmart.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/buyer/{buyerId}")
    public ResponseEntity<List<Order>> getOrdersByBuyer(@PathVariable UUID buyerId) {
        List<Order> orders = orderService.getOrdersByBuyer(buyerId);
        return ResponseEntity.ok(orders);
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<Order> updateOrderStatus(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> requestBody) {

        String newStatus = requestBody.get("status");
        Order updatedOrder = orderService.updateOrderStatus(orderId, newStatus);
        return ResponseEntity.ok(updatedOrder);
    }

    @PatchMapping("/{orderId}/tracking")
    public ResponseEntity<Order> updateTrackingNumber(
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> requestBody) {

        String trackingNumber = requestBody.get("trackingNumber");
        Order updatedOrder = orderService.updateTrackingNumber(orderId, trackingNumber);
        return ResponseEntity.ok(updatedOrder);
    }

    @PostMapping("/test-create")
    public ResponseEntity<Order> createOrderTest(@RequestBody Map<String, String> requestBody) {
        UUID listingId = UUID.fromString(requestBody.get("listingId"));
        UUID buyerId = UUID.fromString(requestBody.get("buyerId"));

        Order newOrder = orderService.createOrderAutomatically(listingId, buyerId);
        return ResponseEntity.ok(newOrder);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> deleteOrder(@PathVariable UUID orderId) {
        orderService.deleteOrder(orderId);

        return ResponseEntity.ok(Map.of("message", "Pesanan berhasil dihapus"));
    }
}