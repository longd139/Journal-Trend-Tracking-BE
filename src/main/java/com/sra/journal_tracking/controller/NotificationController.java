package com.sra.journal_tracking.controller;

import com.sra.journal_tracking.dto.notification.NotificationResponse;
import com.sra.journal_tracking.dto.notification.UnreadCountResponse;
import com.sra.journal_tracking.dto.response.AppResponse;
import com.sra.journal_tracking.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "Lấy danh sách notification",
            description = "Lấy danh sách thông báo của user hiện tại, mới nhất trước. "
                        + "Filter: không truyền = tất cả, 'unread' = chỉ lấy chưa đọc."
    )
    @GetMapping
    public ResponseEntity<AppResponse<List<NotificationResponse>>> getNotifications(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String filter) {

        if (page < 0) page = 0;
        if (size < 1) size = 1;
        if (size > 50) size = 50;

        List<NotificationResponse> notifications = notificationService.getNotifications(
                authentication.getName(), page, size, filter);
        return ResponseEntity.ok(AppResponse.success("Notifications retrieved", notifications));
    }

    @Operation(
            summary = "Đếm notification chưa đọc",
            description = "Trả về số lượng notification chưa đọc của user hiện tại. "
                        + "Dùng cho bell badge trên header."
    )
    @GetMapping("/unread-count")
    public ResponseEntity<AppResponse<UnreadCountResponse>> getUnreadCount(
            Authentication authentication) {

        UnreadCountResponse count = notificationService.getUnreadCount(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("Unread count retrieved", count));
    }

    @Operation(
            summary = "Đánh dấu 1 notification là đã đọc",
            description = "Đánh dấu một notification cụ thể là đã đọc. "
                        + "Nếu đã đọc rồi thì không làm gì (idempotent)."
    )
    @PutMapping("/{notifId}/read")
    public ResponseEntity<AppResponse<Void>> markAsRead(
            Authentication authentication,
            @PathVariable UUID notifId) {

        notificationService.markAsRead(authentication.getName(), notifId);
        return ResponseEntity.ok(AppResponse.success("Notification marked as read"));
    }

    @Operation(
            summary = "Đánh dấu tất cả là đã đọc",
            description = "Đánh dấu tất cả notification của user hiện tại là đã đọc. "
                        + "Dùng 1 query UPDATE duy nhất, không load từng record."
    )
    @PutMapping("/read-all")
    public ResponseEntity<AppResponse<Void>> markAllAsRead(
            Authentication authentication) {

        notificationService.markAllAsRead(authentication.getName());
        return ResponseEntity.ok(AppResponse.success("All notifications marked as read"));
    }

    @Operation(
            summary = "Xóa 1 notification",
            description = "Xóa một notification của user hiện tại. "
                        + "Chỉ chủ sở hữu mới có quyền xóa."
    )
    @DeleteMapping("/{notifId}")
    public ResponseEntity<AppResponse<Void>> deleteNotification(
            Authentication authentication,
            @PathVariable UUID notifId) {

        notificationService.deleteNotification(authentication.getName(), notifId);
        return ResponseEntity.ok(AppResponse.success("Notification deleted"));
    }
}
