package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.admin.AdminUserResponse;
import com.sra.journal_tracking.dto.admin.UserRoleRequest;
import com.sra.journal_tracking.dto.admin.UserStatusRequest;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<AppResponse<Page<AdminUserResponse>>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(AppResponse.success(
                "Admin users retrieved",
                adminService.getUsers(page, size, search)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AppResponse<AdminUserResponse>> updateUserStatus(
            @PathVariable UUID id,
            @RequestBody UserStatusRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "User status updated",
                adminService.updateUserStatus(id, request.getActive())));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<AppResponse<AdminUserResponse>> updateUserRole(
            @PathVariable UUID id,
            @RequestBody UserRoleRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "User role updated",
                adminService.updateUserRole(id, request.getRoleName())));
    }
}
