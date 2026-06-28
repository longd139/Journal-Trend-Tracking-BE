package com.sra.journal_tracking.service;

import com.sra.journal_tracking.dto.notification.NotificationResponse;
import com.sra.journal_tracking.dto.notification.UnreadCountResponse;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    /** Lấy danh sách notification của user, phân trang. filter=unread để lấy chưa đọc. */
    List<NotificationResponse> getNotifications(String email, int page, int size, String filter);

    /** Đếm số notification chưa đọc (dùng cho bell badge). */
    UnreadCountResponse getUnreadCount(String email);

    /** Đánh dấu 1 notification là đã đọc. */
    void markAsRead(String email, UUID notifId);

    /** Đánh dấu tất cả là đã đọc. */
    void markAllAsRead(String email);

    /** Xóa 1 notification. */
    void deleteNotification(String email, UUID notifId);
}
