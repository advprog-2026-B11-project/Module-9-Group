package com.example.bidmart.order.service;

import com.example.bidmart.bidding.exception.ResourceNotFoundException; // Memakai exception yang sudah ada
import com.example.bidmart.order.model.Order;
import com.example.bidmart.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    public Order createOrderAutomatically(UUID listingId, UUID buyerId) {
        Order newOrder = new Order(listingId, buyerId, "CREATED");
        return orderRepository.save(newOrder);
    }

    public List<Order> getOrdersByBuyer(UUID buyerId) {
        return orderRepository.findByBuyerId(buyerId);
    }

    public Order updateOrderStatus(UUID orderId, String newStatus) {
        Order order = getOrderOrThrow(orderId);
        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public Order updateTrackingNumber(UUID orderId, String trackingNumber) {
        Order order = getOrderOrThrow(orderId);
        order.setTrackingNumber(trackingNumber);
        order.setStatus("SHIPPED");
        return orderRepository.save(order);
    }

    public void deleteOrder(UUID orderId) {
        Order order = getOrderOrThrow(orderId);
        orderRepository.delete(order);
    }

    private Order getOrderOrThrow(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pesanan tidak ditemukan dengan ID: " + orderId));
    }
}