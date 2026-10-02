package com.aplicafacil.auth.application.exception;

/** Ya existe una cuenta con ese email. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Ya existe una cuenta con el email " + email);
    }
}
