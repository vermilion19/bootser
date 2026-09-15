package com.booster.dday.anniversary.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@link NotifyOffsets} ↔ {@code notify_offsets}.
 *
 * <p>정렬해 쉼표로 잇는 규칙이 한 곳에만 있게 한다 (SCHEMA §1.3).
 */
@Converter
public class NotifyOffsetsConverter implements AttributeConverter<NotifyOffsets, String> {

    @Override
    public String convertToDatabaseColumn(NotifyOffsets attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NotifyOffsets convertToEntityAttribute(String column) {
        return column == null ? null : new NotifyOffsets(column);
    }
}
