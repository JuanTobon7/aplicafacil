package com.aplicafacil.linkedin.application.exception;

import java.util.UUID;

/** La sesión venció, no existe o no se pudo descifrar. La conexión queda EXPIRED y el front pide reconectar. */
public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(UUID userId) {
        super("La sesión de LinkedIn del usuario " + userId + " expiró; hay que reconectar");
    }
}
