package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.notification.NotificationResponse;
import com.sra.journal_tracking.entity.jpa.NotificationType;

import java.util.List;

/**
 * Service chịu trách nhiệm broadcast notification tới tất cả admin
 * và quản lý notification dashboard dành riêng cho admin.
 */
public interface AdminNotificationService {

    /**
     * Broadcast 1 notification tới tất cả admin đang active.
     * Tạo 1 row Notification cho mỗi admin, publish SSE event real-time,
     * và gửi email thông báo (nếu email được cấu hình).
     *
     * @param type    loại notification (NEW_USER, SYNC_COMPLETED, ...)
     * @param title   tiêu đề
     * @param message nội dung chi tiết
     */
    void broadcastToAdmins(NotificationType type, String title, String message);

    /**
     * Lấy danh sách notification của 1 admin, có thể lọc theo type.
     *
     * @param email     email của admin
     * @param page      số trang (0-based)
     * @param size      số lượng mỗi trang
     * @param typeFilter tên type để lọc (null = lấy tất cả)
     * @return danh sách NotificationResponse
     */
    List<NotificationResponse> getAdminNotifications(String email, int page, int size, String typeFilter);
}
