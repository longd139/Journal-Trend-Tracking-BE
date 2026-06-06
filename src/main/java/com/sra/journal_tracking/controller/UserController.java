package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.ErrorResponse;
import com.sra.journal_tracking.dto.user.ChangePasswordRequest;
import com.sra.journal_tracking.dto.user.UpdateProfileRequest;
import com.sra.journal_tracking.dto.user.UserDTO;
import com.sra.journal_tracking.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "User", description = "User profile & admin management APIs")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    // ===================== CURRENT USER ENDPOINTS =====================

    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile details of the currently logged-in user based on JWT token.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in / Invalid token",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        return ResponseEntity.ok(userService.getCurrentUser(authentication.getName()));
    }

    @Operation(
            summary = "Update profile",
            description = "Update the username, organization and avatar URL of the currently logged-in user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Update successful",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(authentication.getName(), request));
    }

    @Operation(
            summary = "Change password",
            description = "Change the password for the currently logged-in user. Requires the old password for verification.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully",
                    content = @Content(schema = @Schema(example = "Password updated successfully"))),
            @ApiResponse(responseCode = "400", description = "Incorrect old password / Invalid input data",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/me/password")
    public ResponseEntity<String> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok("Password updated successfully");
    }

    @Operation(
            summary = "Upgrade account",
            description = "Upgrade account from ACADEMIC_USER to RESEARCHER. Only available for users with the ACADEMIC_USER role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Upgrade successful",
                    content = @Content(schema = @Schema(example = "Account upgraded to RESEARCHER"))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (only ACADEMIC_USER can upgrade)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found or RESEARCHER role not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/me/upgrade")
    @PreAuthorize("hasRole('ACADEMIC_USER')")
    public ResponseEntity<String> upgradeAccount(Authentication authentication) {
        userService.upgradeAccount(authentication.getName());
        return ResponseEntity.ok("Account upgraded to RESEARCHER");
    }

    // ===================== ADMIN ENDPOINTS =====================

    @Operation(
            summary = "[ADMIN] Get all users",
            description = "Returns a list of all users in the system. Only accessible by ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (ADMIN only)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(
            summary = "[ADMIN] Get user by ID",
            description = "Returns detailed information of a user by userId. Only accessible by ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Success",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (ADMIN only)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> getUserById(
            @Parameter(description = "UUID of the user to retrieve", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @Operation(
            summary = "[ADMIN] Change user activation status",
            description = "Activate (true) or deactivate (false) a user account. Only accessible by ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status changed successfully",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (ADMIN only)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> changeUserStatus(
            @Parameter(description = "UUID of the user to change status", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID userId,
            @Parameter(description = "true = activate, false = deactivate", required = true, example = "true")
            @RequestParam boolean status) {
        return ResponseEntity.ok(userService.changeUserStatus(userId, status));
    }

    @Operation(
            summary = "[ADMIN] Change user role",
            description = "Assign a new role to a user (e.g., ACADEMIC_USER, RESEARCHER, ADMIN). Only accessible by ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Role changed successfully",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not logged in",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Access denied (ADMIN only)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "User or role not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> changeUserRole(
            @Parameter(description = "UUID of the user to change role", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @PathVariable UUID userId,
            @Parameter(description = "New role name (ACADEMIC_USER, RESEARCHER, ADMIN)", required = true, example = "RESEARCHER")
            @RequestParam String roleName) {
        return ResponseEntity.ok(userService.changeUserRole(userId, roleName));
    }
}