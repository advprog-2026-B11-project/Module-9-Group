package com.example.bidmart.user.service;

import com.example.bidmart.user.dto.SessionResponse;
import com.example.bidmart.user.model.Session;
import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.SessionRepository;
import com.example.bidmart.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionServiceImpl implements SessionService {
    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;

    public SessionServiceImpl(SessionRepository sessionRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void enforceSessionLimit(User user, int maxSessions) {
        List<Session> activeSessions = sessionRepository.findByUserIdAndIsRevokedFalseOrderByCreatedAtAsc(user.getId());
        if (activeSessions.size() >= maxSessions) {
            // Revoke the oldest sessions to respect the limit
            int excess = activeSessions.size() - maxSessions + 1;
            for (int i = 0; i < excess; i++) {
                activeSessions.get(i).setRevoked(true);
            }
            sessionRepository.saveAll(activeSessions.subList(0, excess));
        }
    }

    @Override
    @Transactional
    public SessionResponse createSession(User user, String refreshToken, String deviceInfo) {
        Session session = Session.builder()
                .user(user)
                .refreshToken(refreshToken)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .isRevoked(false)
                .deviceInfo(deviceInfo)
                .build();
        sessionRepository.save(session);
        return new SessionResponse(session.getId(), deviceInfo, session.getCreatedAt(), session.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        List<Session> activeSessions = sessionRepository.findByUserIdAndIsRevokedFalseOrderByCreatedAtAsc(user.getId());

        return activeSessions.stream()
                .map(session -> SessionResponse.builder()
                        .id(session.getId())
                        .deviceInfo(session.getDeviceInfo())
                        .createdAt(session.getCreatedAt())
                        .expiresAt(session.getExpiresAt())
                        .build())
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void revokeSession(String username, UUID sessionId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        Session session = sessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found or unauthorized to revoke."));

        session.setRevoked(true);
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void revokeAllSessions(User user) {
        List<Session> activeSessions = sessionRepository.findByUserIdAndIsRevokedFalseOrderByCreatedAtAsc(user.getId());

        if (activeSessions.isEmpty()) {
            return;
        }

        for (Session session : activeSessions) {
            session.setRevoked(true);
        }

        sessionRepository.saveAll(activeSessions);
    }
}