package com.sra.journal_tracking.service.impl;

import com.sra.journal_tracking.dto.admin.AdminUserResponse;
import com.sra.journal_tracking.dto.admin.AuditLogResponse;
import com.sra.journal_tracking.dto.admin.DataSourceRequest;
import com.sra.journal_tracking.dto.admin.DataSourceResponse;
import com.sra.journal_tracking.dto.admin.SyncHistoryResponse;
import com.sra.journal_tracking.dto.admin.SyncTriggerRequest;
import com.sra.journal_tracking.dto.admin.SyncTriggerResponse;
import com.sra.journal_tracking.dto.admin.SystemConfigResponse;
import com.sra.journal_tracking.dto.admin.UsageSummaryResponse;
import com.sra.journal_tracking.entity.jpa.ApiSource;
import com.sra.journal_tracking.entity.jpa.AuditLog;
import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.SyncLog;
import com.sra.journal_tracking.entity.jpa.SystemConfig;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.entity.jpa.UserUsage;
import com.sra.journal_tracking.repository.jpa.ApiSourceRepository;
import com.sra.journal_tracking.repository.jpa.AuditLogRepository;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.SyncLogRepository;
import com.sra.journal_tracking.repository.jpa.SystemConfigRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.repository.jpa.UserUsageRepository;
import com.sra.journal_tracking.service.AdminService;
import com.sra.journal_tracking.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final Set<String> USER_ROLE_TARGETS = Set.of("academic_user", "researcher", "admin");
    private static final Set<String> SUPPORTED_SYNC_SOURCES = Set.of("openalex", "semantic_scholar", "arxiv", "core");
    private static final Map<String, String> CONFIG_ALIASES = Map.ofEntries(
            Map.entry("syncIntervalHours", "sync_interval_hours"),
            Map.entry("topKeywordsDashboard", "top_keywords_dashboard"),
            Map.entry("academicMonthlySearchLimit", "academic_monthly_search_limit"),
            Map.entry("academicMonthlyViewLimit", "academic_monthly_view_limit"),
            Map.entry("academicMonthlyChartLimit", "academic_monthly_chart_limit"),
            Map.entry("academicMaxBookmarks", "academic_max_bookmarks"),
            Map.entry("academicMaxFollows", "academic_max_follows")
    );
    private static final Set<String> NUMERIC_CONFIG_KEYS = Set.of(
            "sync_interval_hours",
            "top_keywords_dashboard",
            "academic_monthly_search_limit",
            "academic_monthly_view_limit",
            "academic_monthly_chart_limit",
            "academic_max_bookmarks",
            "academic_max_follows"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserUsageRepository userUsageRepository;
    private final ApiSourceRepository apiSourceRepository;
    private final SyncLogRepository syncLogRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final AuditLogRepository auditLogRepository;
    private final DataSyncService dataSyncService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = userRepository.searchUsers(clean(search), pageable);
        String currentMonth = YearMonth.now().toString();
        Map<UUID, UserUsage> usageByUserId = loadUsage(users.getContent(), currentMonth);
        return users.map(user -> mapUser(user, usageByUserId.get(user.getUserId()), currentMonth));
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserStatus(UUID userId, Boolean active) {
        if (active == null) {
            throw new IllegalArgumentException("active is required.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (Boolean.FALSE.equals(active) && isAdmin(user)) {
            throw new IllegalArgumentException("Admin accounts cannot be locked.");
        }
        user.setIsActive(active);
        return mapUser(userRepository.save(user), null, YearMonth.now().toString());
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserRole(UUID userId, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        if (!USER_ROLE_TARGETS.contains(normalizedRole)) {
            throw new IllegalArgumentException("roleName must be academic_user or researcher.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        Role role = roleRepository.findByRoleNameIgnoreCase(normalizedRole)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + normalizedRole));
        user.setRole(role);
        return mapUser(userRepository.save(user), null, YearMonth.now().toString());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataSourceResponse> getDataSources() {
        return apiSourceRepository.findAll(Sort.by(Sort.Direction.ASC, "sourceName"))
                .stream()
                .map(this::mapDataSource)
                .toList();
    }

    @Override
    @Transactional
    public DataSourceResponse createDataSource(DataSourceRequest request) {
        validateDataSourceForCreate(request);
        String sourceName = request.getSourceName().trim().toLowerCase(Locale.ROOT);
        if (apiSourceRepository.existsBySourceNameIgnoreCase(sourceName)) {
            throw new IllegalArgumentException("Data source already exists: " + sourceName);
        }
        ApiSource source = ApiSource.builder()
                .sourceName(sourceName)
                .baseUrl(request.getBaseUrl().trim())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .rateLimitRpm(request.getRateLimitRpm())
                .build();
        return mapDataSource(apiSourceRepository.save(source));
    }

    @Override
    @Transactional
    public DataSourceResponse updateDataSource(UUID sourceId, DataSourceRequest request) {
        ApiSource source = apiSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Data source not found."));
        if (hasText(request.getSourceName())) {
            String newName = request.getSourceName().trim().toLowerCase(Locale.ROOT);
            apiSourceRepository.findBySourceNameIgnoreCase(newName)
                    .filter(existing -> !existing.getSourceId().equals(sourceId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("Data source already exists: " + newName);
                    });
            source.setSourceName(newName);
        }
        if (hasText(request.getBaseUrl())) {
            source.setBaseUrl(request.getBaseUrl().trim());
        }
        if (request.getIsActive() != null) {
            source.setIsActive(request.getIsActive());
        }
        if (request.getRateLimitRpm() != null) {
            validatePositive(request.getRateLimitRpm(), "rateLimitRpm");
            source.setRateLimitRpm(request.getRateLimitRpm());
        }
        return mapDataSource(apiSourceRepository.save(source));
    }

    @Override
    @Transactional
    public void deleteDataSource(UUID sourceId) {
        ApiSource source = apiSourceRepository.findById(sourceId)
                .orElseThrow(() -> new IllegalArgumentException("Data source not found."));
        source.setIsActive(false);
        apiSourceRepository.save(source);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncTriggerResponse triggerManualSync(SyncTriggerRequest request) {
        String sourceName = request.getSourceName() != null ? request.getSourceName() : "openalex";
        String normalizedSource = sourceName.trim().toLowerCase(Locale.ROOT);
        ApiSource source = apiSourceRepository.findBySourceNameIgnoreCase(normalizedSource)
                .orElseThrow(() -> new IllegalArgumentException("Data source not found: " + normalizedSource));
        if (!Boolean.TRUE.equals(source.getIsActive())) {
            throw new IllegalArgumentException("Data source is inactive: " + normalizedSource);
        }
        if (!SUPPORTED_SYNC_SOURCES.contains(normalizedSource)) {
            throw new IllegalArgumentException("Manual sync is not supported for source: " + normalizedSource);
        }

        String query = hasText(request.getQuery()) ? request.getQuery().trim() : "machine learning";
        int limit = request.getLimit() != null ? Math.min(200, Math.max(1, request.getLimit())) : 50;
        dataSyncService.triggerManualSyncAsync(normalizedSource, query, limit, request.getYearFrom(), request.getYearTo());

        return SyncTriggerResponse.builder()
                .sourceName(normalizedSource)
                .query(query)
                .limit(limit)
                .yearFrom(request.getYearFrom())
                .yearTo(request.getYearTo())
                .status("accepted")
                .message("Manual sync request accepted and will run in background.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SyncHistoryResponse> getSyncHistory(int page, int size, String status, Boolean manual) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "startedAt"));
        return syncLogRepository.searchHistory(clean(status), manual, pageable)
                .map(this::mapSyncHistory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResponse> getConfigs() {
        return systemConfigRepository.findAll(Sort.by(Sort.Direction.ASC, "configKey"))
                .stream()
                .map(this::mapConfig)
                .toList();
    }

    @Override
    @Transactional
    public List<SystemConfigResponse> updateConfigs(Map<String, Object> payload, String adminEmail) {
        if (payload == null || payload.isEmpty()) {
            throw new IllegalArgumentException("At least one config value is required.");
        }
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found."));
        Map<String, String> updates = normalizeConfigPayload(payload);
        if (updates.isEmpty()) {
            throw new IllegalArgumentException("No supported config keys found.");
        }

        Map<String, SystemConfig> existingByKey = systemConfigRepository.findByConfigKeyIn(updates.keySet())
                .stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, Function.identity()));

        List<SystemConfig> saved = updates.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    validateConfigValue(key, value);
                    SystemConfig config = existingByKey.getOrDefault(key, SystemConfig.builder()
                            .configKey(key)
                            .description("Configured by admin")
                            .build());
                    config.setConfigValue(value);
                    config.setUpdatedAt(LocalDateTime.now());
                    config.setUpdatedBy(admin.getUserId());
                    return systemConfigRepository.save(config);
                })
                .toList();
        return saved.stream().map(this::mapConfig).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(int page, int size, String action, UUID adminId) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page),
                Math.min(100, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditLogRepository.searchAuditLogs(clean(action), adminId, pageable)
                .map(this::mapAuditLog);
    }

    private Map<UUID, UserUsage> loadUsage(List<User> users, String currentMonth) {
        List<UUID> userIds = users.stream().map(User::getUserId).toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userUsageRepository.findByUser_UserIdInAndUsageMonth(userIds, currentMonth)
                .stream()
                .collect(Collectors.toMap(usage -> usage.getUser().getUserId(), Function.identity()));
    }

    private AdminUserResponse mapUser(User user, UserUsage usage, String currentMonth) {
        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .institution(user.getInstitution())
                .roleName(user.getRole() != null ? user.getRole().getRoleName() : null)
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .usage(mapUsage(usage, currentMonth))
                .build();
    }

    private UsageSummaryResponse mapUsage(UserUsage usage, String currentMonth) {
        return UsageSummaryResponse.builder()
                .usageMonth(usage != null ? usage.getUsageMonth() : currentMonth)
                .searchCount(usage != null ? usage.getSearchCount() : 0)
                .viewCount(usage != null ? usage.getViewCount() : 0)
                .chartViewCount(usage != null ? usage.getChartViewCount() : 0)
                .build();
    }

    private DataSourceResponse mapDataSource(ApiSource source) {
        return DataSourceResponse.builder()
                .sourceId(source.getSourceId())
                .sourceName(source.getSourceName())
                .baseUrl(source.getBaseUrl())
                .isActive(source.getIsActive())
                .rateLimitRpm(source.getRateLimitRpm())
                .lastSyncedAt(source.getLastSyncedAt())
                .build();
    }

    private SyncHistoryResponse mapSyncHistory(SyncLog syncLog) {
        ApiSource source = syncLog.getSource();
        return SyncHistoryResponse.builder()
                .logId(syncLog.getLogId())
                .sourceId(source != null ? source.getSourceId() : null)
                .sourceName(source != null ? source.getSourceName() : null)
                .syncType(syncLog.getSyncType())
                .isManual(syncLog.getIsManual())
                .status(syncLog.getStatus())
                .papersFetched(syncLog.getPapersFetched())
                .papersInserted(syncLog.getPapersInserted())
                .papersUpdated(0)
                .errorMessage(syncLog.getErrorMessage())
                .startedAt(syncLog.getStartedAt())
                .completedAt(syncLog.getCompletedAt())
                .build();
    }

    private SystemConfigResponse mapConfig(SystemConfig config) {
        return SystemConfigResponse.builder()
                .configId(config.getConfigId())
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .description(config.getDescription())
                .updatedAt(config.getUpdatedAt())
                .updatedBy(config.getUpdatedBy())
                .build();
    }

    private AuditLogResponse mapAuditLog(AuditLog auditLog) {
        User admin = auditLog.getAdmin();
        return AuditLogResponse.builder()
                .auditId(auditLog.getAuditId())
                .adminId(admin != null ? admin.getUserId() : null)
                .adminEmail(admin != null ? admin.getEmail() : null)
                .action(auditLog.getAction())
                .targetTable(auditLog.getTargetTable())
                .targetId(auditLog.getTargetId())
                .oldValue(auditLog.getOldValue())
                .newValue(auditLog.getNewValue())
                .ipAddress(auditLog.getIpAddress())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }

    private void validateDataSourceForCreate(DataSourceRequest request) {
        if (request == null || !hasText(request.getSourceName()) || !hasText(request.getBaseUrl())) {
            throw new IllegalArgumentException("sourceName and baseUrl are required.");
        }
        if (request.getRateLimitRpm() != null) {
            validatePositive(request.getRateLimitRpm(), "rateLimitRpm");
        }
    }

    private void validatePositive(Integer value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0.");
        }
    }

    private String normalizeRole(String roleName) {
        if (!hasText(roleName)) {
            throw new IllegalArgumentException("roleName is required.");
        }
        return roleName.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, String> normalizeConfigPayload(Map<String, Object> payload) {
        Map<String, String> updates = new LinkedHashMap<>();
        flattenPayload(payload).forEach((rawKey, rawValue) -> {
            if (rawValue == null) {
                return;
            }
            String configKey = CONFIG_ALIASES.getOrDefault(rawKey, rawKey);
            if (hasText(configKey)) {
                updates.put(configKey, rawValue.toString().trim());
            }
        });
        return updates;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> flattenPayload(Map<String, Object> payload) {
        Object nestedConfigs = payload.get("configs");
        if (nestedConfigs instanceof Map<?, ?> nestedMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            nestedMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        return payload;
    }

    private void validateConfigValue(String key, String value) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Config value cannot be blank: " + key);
        }
        if (NUMERIC_CONFIG_KEYS.contains(key)) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0) {
                    throw new IllegalArgumentException("Config value must be non-negative: " + key);
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Config value must be a number: " + key);
            }
        }
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean isAdmin(User user) {
        return user.getRole() != null && "admin".equalsIgnoreCase(user.getRole().getRoleName());
    }
}
