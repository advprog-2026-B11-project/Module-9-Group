package com.example.bidmart.notification.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    @Test
    void testNotificationConstructorsGettersAndSetters() {
        Notification emptyNotification = new Notification();
        assertNotNull(emptyNotification);

        UUID userId = UUID.randomUUID();
        Notification notif = new Notification(userId, "INFO", "Hello World");

        assertEquals(userId, notif.getUserId());
        assertEquals("INFO", notif.getType());
        assertEquals("Hello World", notif.getMessage());
        assertFalse(notif.isRead());

        UUID newId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        LocalDateTime time = LocalDateTime.now();

        notif.setId(newId);
        notif.setUserId(newUserId);
        notif.setType("WARNING");
        notif.setMessage("Updated Message");
        notif.setRead(true);
        notif.setCreatedAt(time);

        assertEquals(newId, notif.getId());
        assertEquals(newUserId, notif.getUserId());
        assertEquals("WARNING", notif.getType());
        assertEquals("Updated Message", notif.getMessage());
        assertTrue(notif.isRead());
        assertEquals(time, notif.getCreatedAt());
    }

    @Test
    void testPrePersistOnCreate() {
        Notification notif = new Notification();

        assertNull(notif.getCreatedAt());

        notif.onCreate();

        assertNotNull(notif.getCreatedAt());
    }
}