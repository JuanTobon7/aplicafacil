package com.aplicafacil.computrabajo.application.exception;

/** La sesión guardada no se pudo descifrar (cambió la llave): hay que iniciar sesión de nuevo. */
public class SessionUnreadableException extends RuntimeException {

    public SessionUnreadableException(Throwable cause) {
        super("No se pudo descifrar la sesión de Computrabajo guardada", cause);
    }
}
