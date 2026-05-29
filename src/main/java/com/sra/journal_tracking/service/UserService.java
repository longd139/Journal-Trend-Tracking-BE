package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.user.ChangePasswordRequest;
import com.sra.journal_tracking.dto.user.UpdateProfileRequest;
import com.sra.journal_tracking.dto.user.UserDTO;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserDTO getCurrentUser(String email);
    UserDTO updateProfile(String email, UpdateProfileRequest request);
    void changePassword(String email, ChangePasswordRequest request);
    void upgradeAccount(String email); // UC-05

    // Admin APIs (UC-20)
    List<UserDTO> getAllUsers();
    UserDTO getUserById(UUID userId);
    UserDTO changeUserStatus(UUID userId, boolean status);
    UserDTO changeUserRole(UUID userId, String roleName);
}
