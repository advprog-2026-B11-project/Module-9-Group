package com.example.bidmart.user.service;

import com.example.bidmart.user.dto.UpdateProfileRequest;
import com.example.bidmart.user.dto.UserProfileResponse;
import com.example.bidmart.user.model.Role;
import com.example.bidmart.user.model.User;
import com.example.bidmart.user.repository.SessionRepository;
import com.example.bidmart.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail("alice@mail.com");
        user.setDisplayName("Alice");
        user.setPhoneNumber("08123456789");
        user.setRole(Role.USER);
        user.setEmailVerified(false);
    }

    @Test
    void updateProfile_shouldUpdateOnlyProvidedFields() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Alice Updated");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserProfileResponse response = userService.updateProfile("alice", request);

        assertEquals("Alice Updated", response.getDisplayName());
        assertEquals("08123456789", response.getPhoneNumber());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateProfile_shouldThrowWhenUserNotFound() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName("Alice Updated");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> userService.updateProfile("alice", request));
    }

    @Test
    void deleteProfile_shouldDeleteSessionsThenUser() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        userService.deleteProfile("alice");

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(sessionRepository, times(1)).deleteAllByUserId(idCaptor.capture());
        assertEquals(user.getId(), idCaptor.getValue());
        verify(userRepository, times(1)).delete(user);
    }

    @Test
    void deleteProfile_shouldThrowWhenUserNotFound() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.deleteProfile("alice")
        );

        assertTrue(exception.getMessage().contains("User not found"));
    }
}
