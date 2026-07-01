package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.user.ChangePasswordRequest;
import com.sra.journal_tracking.dto.user.UpdateProfileRequest;
import com.sra.journal_tracking.dto.user.UserDTO;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController Unit Tests")
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private UserController userController;

    private static final String USER_EMAIL = "test@example.com";
    private UserDTO userDTO;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        userDTO = UserDTO.builder()
                .userId(userId)
                .fullName("Test User")
                .email(USER_EMAIL)
                .institution("Test University")
                .roleName("ACADEMIC_USER")
                .isActive(true)
                .remainingSearches(50)
                .remainingViews(100)
                .build();
        lenient().when(authentication.getName()).thenReturn(USER_EMAIL);
    }

    // ═══════════════════════════════════════════════════════════
    //  GET /api/users/me
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/users/me")
    class GetCurrentUserTests {

        @Test
        @DisplayName("Should return current user profile")
        void getCurrentUser_WhenAuthenticated_ReturnsUserDTO() {
            when(userService.getCurrentUser(USER_EMAIL)).thenReturn(userDTO);

            ResponseEntity<AppResponse<UserDTO>> result = userController.getCurrentUser(authentication);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("User profile retrieved", result.getBody().getMessage());
            assertEquals(USER_EMAIL, result.getBody().getData().getEmail());
            verify(userService).getCurrentUser(USER_EMAIL);
        }

        @Test
        @DisplayName("Should throw AppException when user not found")
        void getCurrentUser_WhenUserNotFound_ThrowsAppException() {
            when(userService.getCurrentUser(USER_EMAIL))
                    .thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

            AppException exception = assertThrows(AppException.class,
                    () -> userController.getCurrentUser(authentication));
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PUT /api/users/me
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/users/me")
    class UpdateProfileTests {

        @Test
        @DisplayName("Should update profile successfully")
        void updateProfile_WithValidRequest_ReturnsUpdatedUserDTO() {
            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .fullName("Updated Name")
                    .institution("New University")
                    .build();
            UserDTO updated = UserDTO.builder()
                    .userId(userId).fullName("Updated Name")
                    .email(USER_EMAIL).institution("New University").build();
            when(userService.updateProfile(eq(USER_EMAIL), any(UpdateProfileRequest.class)))
                    .thenReturn(updated);

            ResponseEntity<AppResponse<UserDTO>> result = userController.updateProfile(authentication, request);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Profile updated", result.getBody().getMessage());
            assertEquals("Updated Name", result.getBody().getData().getFullName());
            verify(userService).updateProfile(USER_EMAIL, request);
        }

        @Test
        @DisplayName("Should update profile with only name change")
        void updateProfile_WithOnlyName_ReturnsUpdatedUserDTO() {
            UpdateProfileRequest request = UpdateProfileRequest.builder().fullName("New Name").build();
            when(userService.updateProfile(eq(USER_EMAIL), any())).thenReturn(userDTO);

            ResponseEntity<AppResponse<UserDTO>> result = userController.updateProfile(authentication, request);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(userService).updateProfile(USER_EMAIL, request);
        }

        @Test
        @DisplayName("Should update profile with empty request body")
        void updateProfile_WithEmptyRequest_ReturnsUserDTO() {
            UpdateProfileRequest request = new UpdateProfileRequest();
            when(userService.updateProfile(eq(USER_EMAIL), any())).thenReturn(userDTO);

            ResponseEntity<AppResponse<UserDTO>> result = userController.updateProfile(authentication, request);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(userService).updateProfile(USER_EMAIL, request);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PUT /api/users/me/password
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/users/me/password")
    class ChangePasswordTests {

        @Test
        @DisplayName("Should change password successfully")
        void changePassword_WithValidRequest_ReturnsSuccess() {
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("oldPass").newPassword("newPass").build();
            doNothing().when(userService).changePassword(eq(USER_EMAIL), any(ChangePasswordRequest.class));

            ResponseEntity<AppResponse<Void>> result = userController.changePassword(authentication, request);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Password updated successfully", result.getBody().getMessage());
            verify(userService).changePassword(USER_EMAIL, request);
        }

        @Test
        @DisplayName("Should throw AppException when old password is incorrect")
        void changePassword_WithWrongOldPassword_ThrowsAppException() {
            ChangePasswordRequest request = ChangePasswordRequest.builder()
                    .oldPassword("wrong").newPassword("newPass").build();
            doThrow(new AppException(ErrorCode.INVALID_CREDENTIALS))
                    .when(userService).changePassword(eq(USER_EMAIL), any());

            AppException exception = assertThrows(AppException.class,
                    () -> userController.changePassword(authentication, request));
            assertEquals(ErrorCode.INVALID_CREDENTIALS, exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  POST /api/users/me/upgrade
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/users/me/upgrade")
    class UpgradeAccountTests {

        @Test
        @DisplayName("Should upgrade account successfully")
        void upgradeAccount_WhenAcademicUser_ReturnsSuccess() {
            doNothing().when(userService).upgradeAccount(USER_EMAIL);

            ResponseEntity<AppResponse<Void>> result = userController.upgradeAccount(authentication);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Account upgraded to RESEARCHER", result.getBody().getMessage());
            verify(userService).upgradeAccount(USER_EMAIL);
        }

        @Test
        @DisplayName("Should throw AppException when user not found")
        void upgradeAccount_WhenUserNotFound_ThrowsAppException() {
            doThrow(new AppException(ErrorCode.USER_NOT_FOUND))
                    .when(userService).upgradeAccount(USER_EMAIL);

            AppException exception = assertThrows(AppException.class,
                    () -> userController.upgradeAccount(authentication));
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GET /api/users (Admin)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/users (Admin)")
    class GetAllUsersTests {

        @Test
        @DisplayName("Should return all users list")
        void getAllUsers_WhenAdmin_ReturnsUserList() {
            List<UserDTO> users = List.of(userDTO, UserDTO.builder().email("user2@test.com").build());
            when(userService.getAllUsers()).thenReturn(users);

            ResponseEntity<AppResponse<List<UserDTO>>> result = userController.getAllUsers();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("Users retrieved", result.getBody().getMessage());
            assertEquals(2, result.getBody().getData().size());
            verify(userService).getAllUsers();
        }

        @Test
        @DisplayName("Should return empty list when no users exist")
        void getAllUsers_WhenNoUsers_ReturnsEmptyList() {
            when(userService.getAllUsers()).thenReturn(List.of());

            ResponseEntity<AppResponse<List<UserDTO>>> result = userController.getAllUsers();

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().getData().isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  GET /api/users/{userId} (Admin)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/users/{userId} (Admin)")
    class GetUserByIdTests {

        @Test
        @DisplayName("Should return user by ID")
        void getUserById_WhenUserExists_ReturnsUserDTO() {
            when(userService.getUserById(userId)).thenReturn(userDTO);

            ResponseEntity<AppResponse<UserDTO>> result = userController.getUserById(userId);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(userId, result.getBody().getData().getUserId());
            verify(userService).getUserById(userId);
        }

        @Test
        @DisplayName("Should throw AppException when user not found")
        void getUserById_WhenNotFound_ThrowsAppException() {
            UUID unknownId = UUID.randomUUID();
            when(userService.getUserById(unknownId))
                    .thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

            AppException exception = assertThrows(AppException.class,
                    () -> userController.getUserById(unknownId));
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PUT /api/users/{userId}/status (Admin)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/users/{userId}/status (Admin)")
    class ChangeUserStatusTests {

        @Test
        @DisplayName("Should activate user successfully")
        void changeUserStatus_ActivateUser_ReturnsUpdatedUserDTO() {
            UserDTO activated = UserDTO.builder().userId(userId).isActive(true).build();
            when(userService.changeUserStatus(userId, true)).thenReturn(activated);

            ResponseEntity<AppResponse<UserDTO>> result = userController.changeUserStatus(userId, true);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("User status updated", result.getBody().getMessage());
            assertTrue(result.getBody().getData().getIsActive());
            verify(userService).changeUserStatus(userId, true);
        }

        @Test
        @DisplayName("Should deactivate user successfully")
        void changeUserStatus_DeactivateUser_ReturnsUpdatedUserDTO() {
            UserDTO deactivated = UserDTO.builder().userId(userId).isActive(false).build();
            when(userService.changeUserStatus(userId, false)).thenReturn(deactivated);

            ResponseEntity<AppResponse<UserDTO>> result = userController.changeUserStatus(userId, false);

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertFalse(result.getBody().getData().getIsActive());
            verify(userService).changeUserStatus(userId, false);
        }

        @Test
        @DisplayName("Should throw AppException when user not found for status change")
        void changeUserStatus_WhenUserNotFound_ThrowsAppException() {
            when(userService.changeUserStatus(userId, false))
                    .thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

            AppException exception = assertThrows(AppException.class,
                    () -> userController.changeUserStatus(userId, false));
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PUT /api/users/{userId}/role (Admin)
    // ═══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/users/{userId}/role (Admin)")
    class ChangeUserRoleTests {

        @Test
        @DisplayName("Should change role to RESEARCHER successfully")
        void changeUserRole_ToResearcher_ReturnsUpdatedUserDTO() {
            UserDTO updated = UserDTO.builder().userId(userId).roleName("RESEARCHER").build();
            when(userService.changeUserRole(userId, "RESEARCHER")).thenReturn(updated);

            ResponseEntity<AppResponse<UserDTO>> result = userController.changeUserRole(userId, "RESEARCHER");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals("User role updated", result.getBody().getMessage());
            assertEquals("RESEARCHER", result.getBody().getData().getRoleName());
            verify(userService).changeUserRole(userId, "RESEARCHER");
        }

        @Test
        @DisplayName("Should change role to ADMIN successfully")
        void changeUserRole_ToAdmin_ReturnsUpdatedUserDTO() {
            UserDTO updated = UserDTO.builder().userId(userId).roleName("ADMIN").build();
            when(userService.changeUserRole(userId, "ADMIN")).thenReturn(updated);

            ResponseEntity<AppResponse<UserDTO>> result = userController.changeUserRole(userId, "ADMIN");

            assertEquals("ADMIN", result.getBody().getData().getRoleName());
            verify(userService).changeUserRole(userId, "ADMIN");
        }

        @Test
        @DisplayName("Should change role with empty role name (service handles validation)")
        void changeUserRole_WithEmptyRoleName_InvokesService() {
            when(userService.changeUserRole(userId, "")).thenReturn(userDTO);

            ResponseEntity<AppResponse<UserDTO>> result = userController.changeUserRole(userId, "");

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(userService).changeUserRole(userId, "");
        }

        @Test
        @DisplayName("Should throw AppException when user not found for role change")
        void changeUserRole_WhenUserNotFound_ThrowsAppException() {
            when(userService.changeUserRole(userId, "RESEARCHER"))
                    .thenThrow(new AppException(ErrorCode.USER_NOT_FOUND));

            AppException exception = assertThrows(AppException.class,
                    () -> userController.changeUserRole(userId, "RESEARCHER"));
            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }
}
