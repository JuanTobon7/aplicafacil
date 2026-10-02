package com.aplicafacil.people.application.exception;

/** Otra persona ya usa el email: conflicto de datos que no se arregla reintentando. */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Otra persona ya usa el email " + email);
    }
}
