package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Chuyển đổi giữa Java enum {@link ReportStatus} và giá trị lưu trong DB.
 * DB dùng lowercase, Java enum dùng UPPER_SNAKE_CASE.
 */
@Converter(autoApply = false)
public class ReportStatusConverter implements AttributeConverter<ReportStatus, String> {

    @Override
    public String convertToDatabaseColumn(ReportStatus attribute) {
        if (attribute == null) return null;
        return attribute.name().toLowerCase();
    }

    @Override
    public ReportStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        return ReportStatus.valueOf(dbData.toUpperCase());
    }
}
