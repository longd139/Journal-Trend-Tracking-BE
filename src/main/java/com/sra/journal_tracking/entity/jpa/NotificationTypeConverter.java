package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Chuyển đổi giữa Java enum {@link NotificationType} và giá trị lưu trong DB.
 * DB dùng lowercase snake_case ('new_paper', 'trend_alert', ...),
 * Java enum dùng UPPER_SNAKE_CASE.
 */
@Converter(autoApply = false)
public class NotificationTypeConverter implements AttributeConverter<NotificationType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationType attribute) {
        if (attribute == null) return null;
        return attribute.name().toLowerCase();
    }

    @Override
    public NotificationType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return null;
        return NotificationType.valueOf(dbData.toUpperCase());
    }
}
