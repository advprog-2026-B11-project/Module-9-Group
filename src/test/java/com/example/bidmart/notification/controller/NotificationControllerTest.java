package com.example.bidmart.notification.controller;

import com.example.bidmart.notification.model.Notification;
import com.example.bidmart.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private UUID userId;
    private UUID notificationId;
    private Notification notification;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        notification = new Notification(userId, "TEST_TYPE", "Test Message");
        notification.setId(notificationId);
    }

    @Test
    void getUserNotifications_returnsOk() {
        when(notificationService.getUserNotifications(userId)).thenReturn(Arrays.asList(notification));

        ResponseEntity<List<Notification>> response = notificationController.getUserNotifications(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getUnreadNotifications_returnsOk() {
        when(notificationService.getUnreadNotifications(userId)).thenReturn(Arrays.asList(notification));

        ResponseEntity<List<Notification>> response = notificationController.getUnreadNotifications(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().get(0).isRead());
    }

    @Test
    void markAsRead_returnsOk() {
        notification.setRead(true);
        when(notificationService.markAsRead(notificationId)).thenReturn(notification);

        ResponseEntity<Notification> response = notificationController.markAsRead(notificationId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isRead());
    }

    @Test
    void createTestNotification_returnsOk() {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("userId", userId.toString());
        requestBody.put("type", "TEST_TYPE");
        requestBody.put("message", "Test Message");

        when(notificationService.createNotification(eq(userId), eq("TEST_TYPE"), eq("Test Message")))
                .thenReturn(notification);

        ResponseEntity<Notification> response = notificationController.createTestNotification(requestBody);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(userId, response.getBody().getUserId());
    }

    @Test
    void markAllAsRead_returnsOk() {
        doNothing().when(notificationService).markAllAsRead(userId);

        ResponseEntity<Map<String, String>> response = notificationController.markAllAsRead(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("message"));
        verify(notificationService, times(1)).markAllAsRead(userId);
    }

    @Test
    void deleteNotification_returnsOk() {
        doNothing().when(notificationService).deleteNotification(notificationId);

        ResponseEntity<Map<String, String>> response = notificationController.deleteNotification(notificationId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().containsKey("message"));
        verify(notificationService, times(1)).deleteNotification(notificationId);
    }
}