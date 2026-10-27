package com.aplicafacil.linkedin.application.exception;

import java.util.UUID;

/** Otra automatización tiene la sesión del usuario. Evita dos ejecuciones simultáneas sobre la misma sesión. */
public class SessionBusyException extends RuntimeException {

    public SessionBusyException(UUID userId) {
        super("La sesión de LinkedIn del usuario " + userId + " está en uso por otra automatización");
    }
}
