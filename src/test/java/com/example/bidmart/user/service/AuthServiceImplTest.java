package com.example.bidmart.user.service;

import com.example.bidmart.user.dto.AuthResponse;
import com.example.bidmart.user.dto.LoginRequest;
import com.example.bidmart.user.dto.RegisterRequest;
import com.example.bidmart.user.model.Role;
import com.example.bidmart.user.model.Session;
import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.RoleRepository;
import com.example.bidmart.user.repository.SessionRepository;
import com.example.bidmart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private SessionService sessionService;
    @Mock
    private MfaService mfaService;
    @Mock
    private RoleRepository roleRepository;

    private AuthServiceImpl authService;

    private User mockUser;
    private Role mockRole;

    @BeforeEach
    void setUp() {
        mockRole = new Role(UUID.randomUUID(), "USER", new HashSet<>());

        mockUser = new User();
        mockUser.setId(UUID.randomUUID());
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@mail.com");
        mockUser.setPassword("encoded-password");
        mockUser.setRole(mockRole);
        mockUser.setDisplayName("Test User");

        authService = new AuthServiceImpl(
                userRepository,
                sessionRepository,
                passwordEncoder,
                jwtService,
                sessionService,
                mfaService,
                roleRepository 
        );
    }

    @Test
    void register_shouldSaveUserAndReturnResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("new@mail.com");
        request.setPassword("password123");
        request.setDisplayName("New User");

        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(mockRole));
        
        User savedUser = new User();
        savedUser.setUsername(request.getUsername());
        savedUser.setEmail(request.getEmail());
        savedUser.setRole(mockRole);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("newuser", response.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_shouldThrowExceptionIfUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existinguser");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void register_shouldThrowExceptionIfEmailExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("existing@mail.com");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@mail.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_shouldReturnTokensWhenMfaDisabled() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("testuser");
        request.setPassword("password123");
        mockUser.setMfaEnabled(false);

        when(userRepository.findByEmail("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateAccessToken(mockUser)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(mockUser)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request, "Test Device");

        assertFalse(response.isMfaRequired());
        assertEquals("access-token", response.getAccessToken());
        verify(sessionService, times(1)).createSession(eq(mockUser), eq("refresh-token"), eq("Test Device"));
    }

    @Test
    void login_shouldReturnTempTokenWhenMfaEnabled() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("testuser");
        request.setPassword("password123");
        mockUser.setMfaEnabled(true);

        when(userRepository.findByEmail("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);
        when(jwtService.generateTempToken(mockUser)).thenReturn("temp-token");

        AuthResponse response = authService.login(request, "Test Device");

        assertTrue(response.isMfaRequired());
        assertEquals("temp-token", response.getTempToken());
        assertNull(response.getAccessToken());
    }

    @Test
    void login_shouldThrowExceptionOnWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("testuser");
        request.setPassword("wrong-password");

        when(userRepository.findByEmail("testuser")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> authService.login(request, "Test Device"));
    }

    @Test
    void verifyEmail_shouldReturnTrueOnValidToken() {
        String token = "valid-token";
        when(userRepository.findByVerificationToken(token)).thenReturn(Optional.of(mockUser));

        boolean result = authService.verifyEmail(token);

        assertTrue(result);
        assertTrue(mockUser.isEmailVerified());
        assertNull(mockUser.getVerificationToken());
        verify(userRepository, times(1)).save(mockUser);
    }

    @Test
    void verifyEmail_shouldReturnFalseOnInvalidToken() {
        String token = "invalid-token";
        when(userRepository.findByVerificationToken(token)).thenReturn(Optional.empty());

        boolean result = authService.verifyEmail(token);

        assertFalse(result);
        verify(userRepository, never()).save(any(User.class));
    }
    
    @Test
    void refreshToken_shouldGenerateNewTokens() {
        String oldRefreshToken = "old-refresh-token";
        Session session = new Session();
        session.setUser(mockUser);
        session.setRefreshToken(oldRefreshToken);
        session.setRevoked(false);
        session.setExpiresAt(Instant.now().plusSeconds(3600));
        session.setDeviceInfo("Test Device");

        when(sessionRepository.findByRefreshToken(oldRefreshToken)).thenReturn(Optional.of(session));
        when(jwtService.generateAccessToken(mockUser)).thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(mockUser)).thenReturn("new-refresh-token");

        AuthResponse response = authService.refreshToken(oldRefreshToken);

        assertEquals("new-access-token", response.getAccessToken());
        assertTrue(session.isRevoked());
        verify(sessionRepository, times(1)).save(session);
        verify(sessionService, times(1)).createSession(mockUser, "new-refresh-token", "Test Device");
    }

    @Test
    void refreshToken_shouldThrowWhenExpired() {
        String oldRefreshToken = "old-refresh-token";
        Session session = new Session();
        session.setUser(mockUser);
        session.setRefreshToken(oldRefreshToken);
        session.setRevoked(false);
        session.setExpiresAt(Instant.now().minusSeconds(60));

        when(sessionRepository.findByRefreshToken(oldRefreshToken)).thenReturn(Optional.of(session));

        assertThrows(IllegalArgumentException.class, () -> authService.refreshToken(oldRefreshToken));
        verify(sessionRepository, never()).save(any(Session.class));
        verify(sessionService, never()).createSession(any(User.class), anyString(), anyString());
    }
}