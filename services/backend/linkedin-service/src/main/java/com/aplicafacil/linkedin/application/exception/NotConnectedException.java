package com.aplicafacil.linkedin.application.exception;

import java.util.UUID;

/** El usuario no tiene una sesión de LinkedIn guardada (nunca conectó o se desconectó). */
public class NotConnectedException extends RuntimeException {

    public NotConnectedException(UUID userId) {
        super("El usuario " + userId + " no tiene una sesión de LinkedIn guardada");
    }
}
