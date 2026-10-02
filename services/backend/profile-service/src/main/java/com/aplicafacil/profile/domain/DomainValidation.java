package com.aplicafacil.profile.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Reglas de validación reutilizadas por el dominio de perfiles.
 * Lanza {@link IllegalArgumentException}: un valor inválido nunca llega a un objeto de dominio.
 */
final class DomainValidation {

    private DomainValidation() {
    }

    static <T> T requireNonNull(T value, String field) {
        return Objects.requireNonNull(value, () -> field + " es obligatorio");
    }

    /** Texto obligatorio: se recorta y no puede quedar vacío ni superar {@code maxLength}. */
    static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " es obligatorio");
        }
        return checkLength(value.strip(), field, maxLength);
    }

    /** Texto opcional: vacío o en blanco se normaliza a {@code null}. */
    static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return checkLength(value.strip(), field, maxLength);
    }

    /** {@code endDate} opcional (en curso); si existe no puede ser anterior a {@code startDate}. */
    static void requireValidPeriod(LocalDate startDate, LocalDate endDate) {
        requireNonNull(startDate, "startDate");
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate no puede ser anterior a startDate");
        }
    }

    private static String checkLength(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " no puede superar " + maxLength + " caracteres");
        }
        return value;
    }
}
