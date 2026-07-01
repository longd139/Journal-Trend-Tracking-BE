package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class PdfRequestStatusConverter implements AttributeConverter<PdfRequestStatus, String> {

    @Override
    public String convertToDatabaseColumn(PdfRequestStatus attribute) {
        return attribute != null ? attribute.name().toLowerCase() : null;
    }

    @Override
    public PdfRequestStatus convertToEntityAttribute(String dbData) {
        return dbData != null && !dbData.isBlank()
                ? PdfRequestStatus.valueOf(dbData.toUpperCase())
                : null;
    }
}
