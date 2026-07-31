package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Chuyển đổi giữa Java enum {@link ReportType} và giá trị lưu trong DB.
 * DB dùng lowercase, Java enum dùng UPPER_SNAKE_CASE.
 */
@Converter(autoApply = false)
public class ReportTypeConverter implements AttributeConverter<ReportType, String> {

    @Override
    public String convertToDatabaseColumn(ReportType attribute) {
        if (attribute == null) return null;
        return attribute.name().toLowerCase();
    }

    @Override
    public ReportType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        return ReportType.valueOf(dbData.toUpperCase());
    }
}
