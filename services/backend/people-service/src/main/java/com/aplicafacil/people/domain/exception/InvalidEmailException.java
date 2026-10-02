package com.aplicafacil.people.domain.exception;

/** Email con formato inválido. */
public class InvalidEmailException extends DomainException {

    public InvalidEmailException() {
        super("email no tiene un formato válido");
    }
}
