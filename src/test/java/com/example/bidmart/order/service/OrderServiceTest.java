package com.example.bidmart.order.service;

import com.example.bidmart.order.model.Order;
import com.example.bidmart.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    private UUID orderId;
    private UUID buyerId;
    private UUID listingId;
    private Order order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        buyerId = UUID.randomUUID();
        listingId = UUID.randomUUID();
        order = new Order(listingId, buyerId, "CREATED");
        order.setId(orderId);
    }

    @Test
    void createOrderAutomatically_success() {
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        Order result = orderService.createOrderAutomatically(listingId, buyerId);
        assertNotNull(result);
        assertEquals("CREATED", result.getStatus());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void getOrdersByBuyer_success() {
        when(orderRepository.findByBuyerId(buyerId)).thenReturn(Arrays.asList(order));
        List<Order> result = orderService.getOrdersByBuyer(buyerId);
        assertEquals(1, result.size());
        assertEquals(orderId, result.get(0).getId());
        verify(orderRepository).findByBuyerId(buyerId);
    }

    @Test
    void updateOrderStatus_success() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        Order result = orderService.updateOrderStatus(orderId, "PAID");
        assertEquals("PAID", result.getStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void updateOrderStatus_notFound_throwsException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> orderService.updateOrderStatus(orderId, "PAID"));
    }

    @Test
    void updateTrackingNumber_success() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        Order result = orderService.updateTrackingNumber(orderId, "RESI123");
        assertEquals("SHIPPED", result.getStatus());
        assertEquals("RESI123", result.getTrackingNumber());
    }

    @Test
    void updateTrackingNumber_notFound_throwsException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> orderService.updateTrackingNumber(orderId, "RESI123"));
    }

    @Test
    void deleteOrder_success() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        orderService.deleteOrder(orderId);
        verify(orderRepository).delete(order);
    }

    @Test
    void deleteOrder_notFound_throwsException() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> orderService.deleteOrder(orderId));
    }
}