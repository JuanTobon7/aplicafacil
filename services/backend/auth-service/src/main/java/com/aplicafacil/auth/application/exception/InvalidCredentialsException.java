package com.aplicafacil.auth.application.exception;

/** La contraseña actual no es correcta. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("La contraseña actual no es correcta");
    }
}
