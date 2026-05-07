package com.example.bidmart.user.service;

import com.example.bidmart.user.model.Session;
import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.SessionRepository;
import com.example.bidmart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    private SessionServiceImpl sessionService;
    private User user;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl(sessionRepository, userRepository);
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
    }

    @Test
    void revokeSession_shouldRevokeAndSave() {
        UUID sessionId = UUID.randomUUID();
        Session session = new Session();
        session.setId(sessionId);
        session.setUser(user);
        session.setRevoked(false);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(sessionRepository.findByIdAndUserId(sessionId, user.getId())).thenReturn(Optional.of(session));

        sessionService.revokeSession("alice", sessionId);

        assertTrue(session.isRevoked());
        verify(sessionRepository, times(1)).save(session);
    }

    @Test
    void revokeSession_shouldThrowWhenSessionNotFound() {
        UUID sessionId = UUID.randomUUID();

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(sessionRepository.findByIdAndUserId(sessionId, user.getId())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> sessionService.revokeSession("alice", sessionId));
        verify(sessionRepository, never()).save(any(Session.class));
    }
}
