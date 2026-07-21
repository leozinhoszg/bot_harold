package com.promagroup.apibridge.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Persiste {@link LocalDateTime} como TEXT ISO-8601 no SQLite.
 *
 * <p>Necessario porque o SQLite nao tem tipo de data: sem isto o Hibernate grava epoch-millis
 * mas le esperando texto formatado, causando "Unparseable date". {@code autoApply} cobre todas
 * as entidades para manter o formato consistente.
 */
@Converter(autoApply = true)
public class LocalDateTimeStringConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.format(FORMAT);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        // Tolera tanto ISO ("2026-07-20T17:00:00") quanto o formato do datetime('now') do SQLite
        // ("2026-07-20 17:00:00"), caso alguma linha venha do DEFAULT da coluna.
        return LocalDateTime.parse(dbData.replace(' ', 'T'), FORMAT);
    }
}
