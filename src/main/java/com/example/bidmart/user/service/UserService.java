package com.example.bidmart.user.service;

import com.example.bidmart.user.dto.UpdateProfileRequest;
import com.example.bidmart.user.dto.UserProfileResponse;
import java.util.UUID;

//Abstraction for user profile operations.
public interface UserService {
    UserProfileResponse getCurrentUser(String username);
    UserProfileResponse updateProfile(String username, UpdateProfileRequest request);
    void deleteProfile(String username);
}
