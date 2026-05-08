package com.example.bidmart.user.controller;

import com.example.bidmart.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')") 
@CrossOrigin(origins = "*")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/{userId}/deactivate")
    public ResponseEntity<String> deactivateUser(@PathVariable UUID userId) {
        userService.deactivateUser(userId);
        return ResponseEntity.ok("The user account has been successfully deactivated and all sessions have been revoked.");
    }
}