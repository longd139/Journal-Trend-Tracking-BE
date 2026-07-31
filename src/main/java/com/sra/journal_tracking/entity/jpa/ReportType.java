package com.sra.journal_tracking.entity.jpa;

/**
 * Loại báo cáo từ người dùng.
 */
public enum ReportType {

    /** Vấn đề với file PDF */
    PDF_ISSUE,

    /** Lỗi nội dung bài báo */
    CONTENT_ERROR,

    /** Loại khác */
    OTHER
}
