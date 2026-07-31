package com.sra.journal_tracking.entity.jpa;

/**
 * Trạng thái xử lý của user report.
 */
public enum ReportStatus {

    /** Mới tạo, chưa được admin xem */
    PENDING,

    /** Admin đã xem, đang xem xét */
    REVIEWED,

    /** Đã giải quyết */
    RESOLVED,

    /** Bị từ chối / bỏ qua */
    DISMISSED
}
