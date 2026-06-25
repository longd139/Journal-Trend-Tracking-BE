package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.admin.AdminUserResponse;
import com.sra.journal_tracking.dto.admin.AuditLogResponse;
import com.sra.journal_tracking.dto.admin.DataSourceRequest;
import com.sra.journal_tracking.dto.admin.DataSourceResponse;
import com.sra.journal_tracking.dto.admin.SyncHistoryResponse;
import com.sra.journal_tracking.dto.admin.SyncTriggerRequest;
import com.sra.journal_tracking.dto.admin.SyncTriggerResponse;
import com.sra.journal_tracking.dto.admin.SystemConfigResponse;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AdminService {
    Page<AdminUserResponse> getUsers(int page, int size, String search);
    AdminUserResponse updateUserStatus(UUID userId, Boolean active);
    AdminUserResponse updateUserRole(UUID userId, String roleName);

    List<DataSourceResponse> getDataSources();
    DataSourceResponse createDataSource(DataSourceRequest request);
    DataSourceResponse updateDataSource(UUID sourceId, DataSourceRequest request);
    void deleteDataSource(UUID sourceId);

    SyncTriggerResponse triggerManualSync(SyncTriggerRequest request);
    Page<SyncHistoryResponse> getSyncHistory(int page, int size, String status, Boolean manual);

    List<SystemConfigResponse> getConfigs();
    List<SystemConfigResponse> updateConfigs(Map<String, Object> payload, String adminEmail);

    Page<AuditLogResponse> getAuditLogs(int page, int size, String action, UUID adminId);
}
