package com.aplicafacil.auth.application.exception;

import java.util.UUID;

/** No existe el usuario. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(UUID id) {
        super("No existe el usuario " + id);
    }
}
