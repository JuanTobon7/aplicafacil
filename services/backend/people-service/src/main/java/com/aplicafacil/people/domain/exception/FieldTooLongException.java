package com.aplicafacil.people.domain.exception;

/** Texto que supera la longitud máxima permitida. */
public class FieldTooLongException extends DomainException {

    public FieldTooLongException(String field, int maxLength) {
        super(field + " no puede superar " + maxLength + " caracteres");
    }
}
