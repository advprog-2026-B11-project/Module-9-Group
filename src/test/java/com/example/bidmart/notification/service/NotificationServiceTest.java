package com.example.bidmart.notification.service;

import com.example.bidmart.notification.model.Notification;
import com.example.bidmart.notification.repository.NotificationRepository;
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
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

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
    void createNotification_success() {
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = notificationService.createNotification(userId, "TEST_TYPE", "Test Message");

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("TEST_TYPE", result.getType());
        assertEquals("Test Message", result.getMessage());
        assertFalse(result.isRead());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void getUserNotifications_returnsList() {
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Arrays.asList(notification));

        List<Notification> result = notificationService.getUserNotifications(userId);

        assertEquals(1, result.size());
        assertEquals(notificationId, result.get(0).getId());
        verify(notificationRepository, times(1)).findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void getUnreadNotifications_returnsList() {
        when(notificationRepository.findByUserIdAndIsReadFalse(userId))
                .thenReturn(Arrays.asList(notification));

        List<Notification> result = notificationService.getUnreadNotifications(userId);

        assertEquals(1, result.size());
        assertFalse(result.get(0).isRead());
        verify(notificationRepository, times(1)).findByUserIdAndIsReadFalse(userId);
    }

    @Test
    void markAsRead_success() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        Notification result = notificationService.markAsRead(notificationId);

        assertTrue(result.isRead());
        verify(notificationRepository, times(1)).save(notification);
    }

    @Test
    void markAsRead_notFound_throwsException() {
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, () -> {
            notificationService.markAsRead(notificationId);
        });

        assertTrue(exception.getMessage().contains("Notifikasi tidak ditemukan"));
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markAllAsRead_success() {
        Notification notif2 = new Notification(userId, "TYPE_2", "Message 2");
        List<Notification> unreadList = Arrays.asList(notification, notif2);

        when(notificationRepository.findByUserIdAndIsReadFalse(userId)).thenReturn(unreadList);

        notificationService.markAllAsRead(userId);

        assertTrue(notification.isRead());
        assertTrue(notif2.isRead());
        verify(notificationRepository, times(1)).saveAll(unreadList);
    }

    @Test
    void deleteNotification_success() {
        doNothing().when(notificationRepository).deleteById(notificationId);

        notificationService.deleteNotification(notificationId);

        verify(notificationRepository, times(1)).deleteById(notificationId);
    }
}