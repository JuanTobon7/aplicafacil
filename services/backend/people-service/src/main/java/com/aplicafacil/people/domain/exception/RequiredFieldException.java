package com.aplicafacil.people.domain.exception;

/** Campo obligatorio nulo o en blanco. */
public class RequiredFieldException extends DomainException {

    public RequiredFieldException(String field) {
        super(field + " es obligatorio");
    }
}
