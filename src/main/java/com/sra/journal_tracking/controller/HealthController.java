package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.dto.response.SystemStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "System Health", description = "System health check and readiness probe")
public class HealthController {

    private final DataSource dataSource;
    private final Driver neo4jDriver;

    @Operation(summary = "System readiness check", description = "Returns system readiness status. Use this endpoint to verify the backend is fully up and ready to serve requests.")
    @ApiResponse(responseCode = "200", description = "System is ready")
    @GetMapping("/health")
    public ResponseEntity<AppResponse<SystemStatusResponse>> healthCheck() {
        boolean dbConnected = checkDatabaseConnection();
        boolean neo4jConnected = checkNeo4jConnection();
        boolean allReady = dbConnected && neo4jConnected;

        SystemStatusResponse status = SystemStatusResponse.builder()
                .status(allReady ? "READY" : "DEGRADED")
                .application("SCITRACK - AI-Powered Academic Research Analytics")
                .version("1.0.0")
                .databaseConnected(dbConnected)
                .neo4jConnected(neo4jConnected)
                .uptime(getUptime())
                .build();

        if (allReady) {
            return ResponseEntity.ok(AppResponse.success("System is fully operational and ready to serve requests", status));
        } else {
            String message = buildDegradedMessage(dbConnected, neo4jConnected);
            return ResponseEntity.ok(AppResponse.of(200, message, status));
        }
    }

    private boolean checkDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(3);
        } catch (Exception e) {
            log.warn("Database connection check failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkNeo4jConnection() {
        try {
            neo4jDriver.verifyConnectivity();
            return true;
        } catch (Exception e) {
            log.warn("Neo4j connection check failed: {}", e.getMessage());
            return false;
        }
    }

    private String buildDegradedMessage(boolean dbConnected, boolean neo4jConnected) {
        if (!dbConnected && !neo4jConnected) {
            return "System is starting up. Both SQL Server and Neo4j are not yet available.";
        } else if (!dbConnected) {
            return "System is partially operational. SQL Server is not yet available. Graph features will still work.";
        } else {
            return "System is partially operational. Neo4j is not yet available. Search features will still work.";
        }
    }

    private String getUptime() {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%d days %d hours %d minutes", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%d hours %d minutes", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d minutes %d seconds", minutes, seconds % 60);
        } else {
            return String.format("%d seconds", seconds);
        }
    }
}
