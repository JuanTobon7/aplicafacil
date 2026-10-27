package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** {@code YearMonth} ⇄ {@code "YYYY-MM"} (VARCHAR(7)). Un valor viejo que no se puede leer queda en {@code null}. */
@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, String> {

    @Override
    public String convertToDatabaseColumn(YearMonth value) {
        return value == null ? null : value.toString();
    }

    @Override
    public YearMonth convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(column.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
