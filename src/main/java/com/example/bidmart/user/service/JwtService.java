package com.example.bidmart.user.service;

import com.example.bidmart.user.model.User;

public interface JwtService {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
    String extractUsername(String token);
    boolean isTokenValid(String token, String username);
    String generateTempToken(User user);
}
