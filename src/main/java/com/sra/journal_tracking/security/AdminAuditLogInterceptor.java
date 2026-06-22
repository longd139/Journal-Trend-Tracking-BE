package com.sra.journal_tracking.security;

import com.sra.journal_tracking.entity.jpa.AuditLog;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.repository.jpa.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditLogInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATION_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AuditLogRepository auditLogRepository;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!shouldAudit(request, response, ex)) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            return;
        }

        User admin = userDetails.getUser();
        try {
            auditLogRepository.save(AuditLog.builder()
                    .admin(admin)
                    .action(trimToLength(request.getMethod() + " " + request.getRequestURI(), 200))
                    .targetTable(resolveTargetTable(request.getRequestURI()))
                    .targetId(resolveTargetId(request.getRequestURI()))
                    .newValue(resolveRequestContext(request))
                    .ipAddress(resolveIpAddress(request))
                    .build());
        } catch (Exception auditEx) {
            log.warn("Failed to write admin audit log for {} {}: {}",
                    request.getMethod(), request.getRequestURI(), auditEx.getMessage());
        }
    }

    private boolean shouldAudit(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        String uri = request.getRequestURI();
        return ex == null
                && response.getStatus() < 400
                && uri.startsWith("/api/admin/")
                && !uri.startsWith("/api/admin/audit-logs")
                && MUTATION_METHODS.contains(request.getMethod());
    }

    private String resolveTargetTable(String uri) {
        if (uri.startsWith("/api/admin/users")) {
            return "USER";
        }
        if (uri.startsWith("/api/admin/data-sources")) {
            return "API_SOURCE";
        }
        if (uri.startsWith("/api/admin/sync")) {
            return "SYNC_LOG";
        }
        if (uri.startsWith("/api/admin/configs")) {
            return "SYSTEM_CONFIG";
        }
        return null;
    }

    private String resolveTargetId(String uri) {
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length; i++) {
            if (("users".equals(parts[i]) || "data-sources".equals(parts[i])) && i + 1 < parts.length) {
                return trimToLength(parts[i + 1], 100);
            }
        }
        return null;
    }

    private String resolveRequestContext(HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return null;
        }
        return trimToLength("query=" + query, 4000);
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return trimToLength(forwardedFor.split(",")[0].trim(), 45);
        }
        return trimToLength(request.getRemoteAddr(), 45);
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
