package com.sra.journal_tracking.entity.jpa;

/**
 * Loại báo cáo từ người dùng.
 */
public enum ReportType {

    /** Vấn đề với file PDF */
    PDF_ISSUE,

    /** Lỗi nội dung bài báo */
    CONTENT_ERROR,

    /** Báo cáo / gắn cờ paper (spam, duplicate, incorrect info, retracted...) */
    PAPER_FLAG,

    /** Loại khác */
    OTHER
}
