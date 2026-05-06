package com.example.bidmart.order.service;

import com.example.bidmart.order.model.Order;
import com.example.bidmart.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    public Order createOrderAutomatically(UUID listingId, UUID buyerId) {
        Order newOrder = new Order(listingId, buyerId, "CREATED");
        return orderRepository.save(newOrder);
    }

    public List<Order> getOrdersByBuyer(UUID buyerId) {
        return orderRepository.findByBuyerId(buyerId);
    }

    public Order updateOrderStatus(UUID orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pesanan tidak ditemukan dengan ID: " + orderId));

        order.setStatus(newStatus);
        return orderRepository.save(order);
    }

    public Order updateTrackingNumber(UUID orderId, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pesanan tidak ditemukan dengan ID: " + orderId));

        order.setTrackingNumber(trackingNumber);
        order.setStatus("SHIPPED");
        return orderRepository.save(order);
    }

    public void deleteOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Pesanan tidak ditemukan dengan ID: " + orderId));

        orderRepository.delete(order);
    }
}