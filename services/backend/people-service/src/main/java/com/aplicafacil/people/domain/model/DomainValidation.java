package com.aplicafacil.people.domain.model;

import com.aplicafacil.people.domain.exception.FieldTooLongException;
import com.aplicafacil.people.domain.exception.RequiredFieldException;

/** Reglas de validación reutilizadas por los modelos de personas. */
final class DomainValidation {

    private DomainValidation() {
    }

    static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new RequiredFieldException(field);
        }
        return value;
    }

    /** Texto obligatorio: se recorta y no puede quedar vacío ni superar {@code maxLength}. */
    static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new RequiredFieldException(field);
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

    private static String checkLength(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new FieldTooLongException(field, maxLength);
        }
        return value;
    }
}
