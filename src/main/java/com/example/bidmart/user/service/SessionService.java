package com.example.bidmart.user.service;

import com.example.bidmart.user.dto.SessionResponse;
import com.example.bidmart.user.model.User;
import java.util.List;
import java.util.UUID;

public interface SessionService {
    void enforceSessionLimit(User user, int maxSessions);
    SessionResponse createSession(User user, String refreshToken, String deviceInfo);
    List<SessionResponse> getActiveSessions(String username);
    void revokeSession(String username, UUID sessionId);
    void revokeAllSessions(User user);
}