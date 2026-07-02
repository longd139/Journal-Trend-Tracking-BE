package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.user.ChangePasswordRequest;
import com.sra.journal_tracking.dto.user.UpdateBackgroundRequest;
import com.sra.journal_tracking.dto.user.UpdateProfileRequest;
import com.sra.journal_tracking.dto.user.UserDTO;
import com.sra.journal_tracking.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<AppResponse<UserDTO>> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(AppResponse.success("User profile retrieved", userService.getCurrentUser(authentication.getName())));
    }

    @PutMapping("/me")
    public ResponseEntity<AppResponse<UserDTO>> updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(AppResponse.success("Profile updated", userService.updateProfile(authentication.getName(), request)));
    }

    @PutMapping("/me/background")
    public ResponseEntity<AppResponse<UserDTO>> updateBackground(
            Authentication authentication,
            @Valid @RequestBody UpdateBackgroundRequest request) {
        return ResponseEntity.ok(AppResponse.success("Background updated", userService.updateBackground(authentication.getName(), request)));
    }

    @PutMapping("/me/password")
    public ResponseEntity<AppResponse<Void>> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(AppResponse.success("Password updated successfully"));
    }

    @PostMapping("/me/upgrade")
    @PreAuthorize("hasRole('ACADEMIC_USER')")
    public ResponseEntity<AppResponse<Void>> upgradeAccount(Authentication authentication) {
        userService.upgradeAccount(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Account upgraded to RESEARCHER"));
    }

    // Admin endpoints
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<List<UserDTO>>> getAllUsers() {
        return ResponseEntity.ok(AppResponse.success("Users retrieved", userService.getAllUsers()));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<UserDTO>> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(AppResponse.success("User retrieved", userService.getUserById(userId)));
    }

    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<UserDTO>> changeUserStatus(
            @PathVariable UUID userId,
            @RequestParam boolean status) {
        return ResponseEntity.ok(AppResponse.success("User status updated", userService.changeUserStatus(userId, status)));
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AppResponse<UserDTO>> changeUserRole(
            @PathVariable UUID userId,
            @RequestParam String roleName) {
        return ResponseEntity.ok(AppResponse.success("User role updated", userService.changeUserRole(userId, roleName)));
    }
}
