package com.example.bidmart.user.controller;

import com.example.bidmart.user.dto.*;
import com.example.bidmart.user.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse mockAuthResponse;

    @BeforeEach
    void setUp() {
        mockAuthResponse = AuthResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .username("testuser")
                .email("test@mail.com")
                .role("USER")
                .build();
    }

    @Test
    void register_shouldReturnCreated() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setEmail("test@mail.com");
        request.setPassword("password123");
        request.setDisplayName("Test User");

        when(authService.register(any(RegisterRequest.class))).thenReturn(mockAuthResponse);

        ResponseEntity<AuthResponse> response = authController.register(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("testuser", response.getBody().getUsername());
        verify(authService, times(1)).register(request);
    }

    @Test
    void login_shouldReturnOk() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("testuser");
        request.setPassword("password123");
        String userAgent = "Test-Agent";

        when(authService.login(any(LoginRequest.class), eq(userAgent))).thenReturn(mockAuthResponse);

        ResponseEntity<AuthResponse> response = authController.login(request, userAgent);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("mock-access-token", response.getBody().getAccessToken());
        verify(authService, times(1)).login(request, userAgent);
    }

    @Test
    void verifyEmail_shouldReturnOkWhenValid() {
        String token = "valid-token";
        when(authService.verifyEmail(token)).thenReturn(true);

        ResponseEntity<String> response = authController.verifyEmail(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Email successfully verified!", response.getBody());
    }

    @Test
    void verifyEmail_shouldReturnBadRequestWhenInvalid() {
        String token = "invalid-token";
        when(authService.verifyEmail(token)).thenReturn(false);

        ResponseEntity<String> response = authController.verifyEmail(token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Verification token is invalid or not found.", response.getBody());
    }

    @Test
    void refresh_shouldReturnOk() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("old-refresh-token");

        when(authService.refreshToken(request.getRefreshToken())).thenReturn(mockAuthResponse);

        ResponseEntity<AuthResponse> response = authController.refresh(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("mock-access-token", response.getBody().getAccessToken());
    }

    @Test
    void verifyMfa_shouldReturnOk() {
        MfaVerificationRequest request = new MfaVerificationRequest();
        request.setTempToken("temp-token");
        request.setCode("123456");

        when(authService.verifyMfaLogin(any(MfaVerificationRequest.class))).thenReturn(mockAuthResponse);

        ResponseEntity<AuthResponse> response = authController.verifyMfa(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("testuser", response.getBody().getUsername());
    }
}