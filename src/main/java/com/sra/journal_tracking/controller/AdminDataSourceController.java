package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.admin.DataSourceRequest;
import com.sra.journal_tracking.dto.admin.DataSourceResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/data-sources")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDataSourceController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<AppResponse<List<DataSourceResponse>>> getDataSources() {
        return ResponseEntity.ok(AppResponse.success(
                "Data sources retrieved",
                adminService.getDataSources()));
    }

    @PostMapping
    public ResponseEntity<AppResponse<DataSourceResponse>> createDataSource(
            @RequestBody DataSourceRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "Data source created",
                adminService.createDataSource(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AppResponse<DataSourceResponse>> updateDataSource(
            @PathVariable UUID id,
            @RequestBody DataSourceRequest request) {
        return ResponseEntity.ok(AppResponse.success(
                "Data source updated",
                adminService.updateDataSource(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<AppResponse<Void>> deleteDataSource(@PathVariable UUID id) {
        adminService.deleteDataSource(id);
        return ResponseEntity.ok(AppResponse.success("Data source disabled"));
    }
}
