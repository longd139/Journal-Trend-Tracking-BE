package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.security.CustomUserDetails;
import com.sra.journal_tracking.service.NotificationEventPublisher.NotificationEvent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE (Server-Sent Events) endpoint for real-time notification push.
 * <p>
 * The browser opens a persistent connection to {@code GET /api/v1/notifications/stream?token=<jwt>}
 * and receives {@code event:notification} frames whenever a new notification is created
 * for the authenticated user.
 * <p>
 * JWT is passed as a query parameter because the browser {@code EventSource} API
 * does not support custom HTTP headers.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification SSE", description = "Real-time notification push via Server-Sent Events")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationSseController {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    /** Per-user registry of active SSE emitters (a user may have multiple tabs open). */
    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @Operation(
            summary = "Subscribe to real-time notifications",
            description = "Opens an SSE stream. The browser receives `event:notification` frames with JSON payload when a new notification is created for the authenticated user. Pass JWT as ?token=<jwt> query parameter.")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        CustomUserDetails details = (CustomUserDetails) authentication.getPrincipal();
        UUID userId = details.getUser().getUserId();

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>())).add(emitter);
        log.debug("SSE connection opened for user {} (total emitters: {})",
                userId, emitters.get(userId).size());

        // Cleanup on disconnect / timeout / error
        Runnable cleanup = () -> {
            List<SseEmitter> userEmitters = emitters.get(userId);
            if (userEmitters != null) {
                userEmitters.remove(emitter);
                if (userEmitters.isEmpty()) {
                    emitters.remove(userId);
                }
                log.debug("SSE connection closed for user {} (remaining: {})",
                        userId, userEmitters.size());
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // Send initial heartbeat so the client knows the connection is alive
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            cleanup.run();
        }

        return emitter;
    }

    /**
     * Push a notification event to all active SSE connections for a user.
     * Called by {@link com.sra.journal_tracking.service.NotificationEventListener}.
     */
    public void sendToUser(UUID userId, NotificationEvent event) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return; // user not connected — notification will be picked up via REST on next poll
        }

        synchronized (userEmitters) {
            userEmitters.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event()
                            .name("notification")
                            .data(event, MediaType.APPLICATION_JSON));
                    return false;
                } catch (IOException e) {
                    log.debug("Removing dead SSE emitter for user {}", userId);
                    return true;
                }
            });
        }

        if (userEmitters.isEmpty()) {
            emitters.remove(userId);
        }
    }
}
