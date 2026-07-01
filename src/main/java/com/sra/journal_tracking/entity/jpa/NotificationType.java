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
    UPGRADE_PROMPT
}
