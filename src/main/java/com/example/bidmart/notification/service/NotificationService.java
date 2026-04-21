package com.example.bidmart.notification.service;

import com.example.bidmart.bidding.exception.ResourceNotFoundException;
import com.example.bidmart.notification.model.Notification;
import com.example.bidmart.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public Notification createNotification(UUID userId, String type, String message) {
        Notification notification = new Notification(userId, type, message);
        return notificationRepository.save(notification);
    }

    public List<Notification> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getUnreadNotifications(UUID userId) {
        return notificationRepository.findByUserIdAndIsReadFalse(userId);
    }

    public Notification markAsRead(UUID notificationId) {
        Notification notification = getNotificationOrThrow(notificationId);
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    public void markAllAsRead(UUID userId) {
        List<Notification> unreadNotifications = notificationRepository.findByUserIdAndIsReadFalse(userId);
        unreadNotifications.forEach(notif -> notif.setRead(true));
        notificationRepository.saveAll(unreadNotifications);
    }

    public void deleteNotification(UUID notificationId) {
        Notification notification = getNotificationOrThrow(notificationId);
        notificationRepository.delete(notification);
    }

    private Notification getNotificationOrThrow(UUID notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notifikasi tidak ditemukan dengan ID: " + notificationId));
    }
}