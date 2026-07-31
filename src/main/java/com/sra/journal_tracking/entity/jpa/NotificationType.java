package com.sra.journal_tracking.entity.jpa;

/**
 * Các loại notification tương ứng với CHECK constraint trong SQL.
 */
public enum NotificationType {

    /** Có bài báo mới trong lĩnh vực đang follow */
    NEW_PAPER,

    /** Cảnh báo xu hướng — keyword/topic đang hot */
    TREND_ALERT,

    /** Thông báo hệ thống */
    SYSTEM,

    /** Nhắc Academic User nâng cấp lên Researcher khi hết lượt */
    UPGRADE_PROMPT,

    // ── Admin-specific notification types ──

    /** Người dùng mới đăng ký */
    NEW_USER,

    /** Người dùng gửi báo cáo (report) */
    USER_REPORT,

    /** Đồng bộ dữ liệu hoàn tất */
    SYNC_COMPLETED,

    /** Đồng bộ dữ liệu thất bại */
    SYNC_FAILED,

    /** Cảnh báo hệ thống (lỗi, cảnh báo) */
    SYSTEM_ALERT,

    /** Cảnh báo nội dung (trending keyword spike, etc.) */
    CONTENT_ALERT
}
