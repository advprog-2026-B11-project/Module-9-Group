package com.example.bidmart.user.controller;

import com.example.bidmart.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AdminUserController adminUserController;

    @Test
    void deactivateUser_shouldReturnOk() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<String> response = adminUserController.deactivateUser(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userService, times(1)).deactivateUser(userId);
    }
}