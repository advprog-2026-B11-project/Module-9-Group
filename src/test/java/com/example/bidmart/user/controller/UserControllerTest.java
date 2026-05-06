package com.example.bidmart.user.controller;

import com.example.bidmart.user.dto.UpdateProfileRequest;
import com.example.bidmart.user.dto.UserProfileResponse;
import com.example.bidmart.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserController userController;

    private UserProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        profileResponse = UserProfileResponse.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .email("alice@mail.com")
                .displayName("Alice")
                .phoneNumber("08123456789")
                .role("USER")
                .isEmailVerified(false)
                .build();

        when(authentication.getName()).thenReturn("alice");
    }

    @Test
    void updateProfile_shouldReturnUpdatedProfile() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Alice Updated");

        UserProfileResponse updated = UserProfileResponse.builder()
                .id(profileResponse.getId())
                .username("alice")
                .email("alice@mail.com")
                .displayName("Alice Updated")
                .phoneNumber("08123456789")
                .role("USER")
                .isEmailVerified(false)
                .build();

        when(userService.updateProfile("alice", request)).thenReturn(updated);

        ResponseEntity<UserProfileResponse> response = userController.updateProfile(authentication, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Alice Updated", response.getBody().getDisplayName());
        verify(userService, times(1)).updateProfile("alice", request);
    }

    @Test
    void deleteProfile_shouldReturnNoContent() {
        ResponseEntity<Void> response = userController.deleteProfile(authentication);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(userService, times(1)).deleteProfile("alice");
    }
}
