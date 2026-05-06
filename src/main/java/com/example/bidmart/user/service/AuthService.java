package com.example.bidmart.user.service;

import com.example.bidmart.user.dto.AuthResponse;
import com.example.bidmart.user.dto.LoginRequest;
import com.example.bidmart.user.dto.MfaVerificationRequest;
import com.example.bidmart.user.dto.RegisterRequest;
import com.example.bidmart.user.model.User;

public interface AuthService {
    
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request, String deviceInfo);
    boolean verifyEmail(String token); 
    AuthResponse refreshToken(String refreshTokenStr);
    AuthResponse verifyMfaLogin(MfaVerificationRequest request);
    AuthResponse finalizeLogin(User user, String deviceInfo);
}