package com.example.bidmart.user.service;

import com.example.bidmart.user.dto.AuthResponse;
import com.example.bidmart.user.dto.LoginRequest;
import com.example.bidmart.user.dto.MfaVerificationRequest;
import com.example.bidmart.user.dto.RegisterRequest;
import com.example.bidmart.user.model.Role;
import com.example.bidmart.user.model.Session;
import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.SessionRepository;
import com.example.bidmart.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final MfaService mfaService;

    public AuthServiceImpl(UserRepository userRepository,
                            SessionRepository sessionRepository,
                            PasswordEncoder passwordEncoder,
                            JwtService jwtService,
                            SessionService sessionService,
                            MfaService mfaService) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.mfaService = mfaService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        validateNewUser(request.getUsername(), request.getEmail());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setDisplayName(request.getDisplayName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.USER);
        user.setEmailVerified(false);

        String verificationToken = UUID.randomUUID().toString();
        user.setVerificationToken(verificationToken);

        User savedUser = userRepository.save(user);

        return mapToAuthResponse(savedUser, null, null);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String deviceInfo) {
        User user = findUserByIdentifier(request.getIdentifier());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid password.");
        }

        if (user.isMfaEnabled()){
            String tempToken = jwtService.generateTempToken(user);
            return AuthResponse.builder().mfaRequired(true).tempToken(tempToken).build();
        }

        return finalizeLogin(user, "Default Device");
    }
    @Override
    @Transactional
    public AuthResponse verifyMfaLogin(MfaVerificationRequest request){
        String username = jwtService.extractUsername(request.getTempToken());
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (!mfaService.verifyCode(user.getMfaSecret(), request.getCode())){
            throw new IllegalArgumentException("Invalid 2FA Code.");
        }

        return finalizeLogin(user, "Default Device");
    }

    @Override
    @Transactional
    public AuthResponse finalizeLogin(User user, String deviceInfo){
        sessionService.enforceSessionLimit(user, 3);
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        sessionService.createSession(user, refreshToken, deviceInfo);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .username(user.getUsername())
            .email(user.getEmail())
            .displayName(user.getDisplayName())
            .role(user.getRole().name())
            .mfaRequired(false)
            .build();
    }

    @Override
    @Transactional
    public boolean verifyEmail(String token) {
        Optional<User> userOptional = userRepository.findByVerificationToken(token);
        if (userOptional.isEmpty()) {
            return false;
        }
        User user = userOptional.get();
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);
        return true;
    }

    private void validateNewUser(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered.");
        }
    }

    private User findUserByIdentifier(String identifier) {
        Optional<User> userOptional = userRepository.findByEmail(identifier);

        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByUsername(identifier);
        }

        return userOptional.orElseThrow(() ->
            new IllegalArgumentException("User with that identifier was not found.")
        );
    }

    private AuthResponse mapToAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .username(user.getUsername())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole().name())
                .build();
    }

    @Override
    public AuthResponse refreshToken(String refreshTokenStr) {
        Session session = sessionRepository.findByRefreshToken(refreshTokenStr).orElseThrow(() -> new IllegalArgumentException("Invalid refresh token."));
        if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())){
            throw new IllegalArgumentException("Refresh token expired or revoked. Please login again.");
        }
        User user = session.getUser();

        session.setRevoked(true);
        sessionRepository.save(session);
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        sessionService.createSession(user, newRefreshToken, session.getDeviceInfo());

        return mapToAuthResponse(user, newAccessToken, newRefreshToken);
    }
}