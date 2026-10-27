package com.aplicafacil.identity.application.exception;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio de identity-service. {@code code} es estable y el front lo usa para decidir qué
 * mostrar (por ejemplo, SESSION_EXPIRED abre la reconexión).
 */
public class IdentityException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public IdentityException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static IdentityException invalidLoginCode() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "INVALID_LOGIN_CODE",
                "El código de inicio de sesión no es válido o ya venció. Vuelve a conectar LinkedIn.");
    }

    public static IdentityException invalidRefreshToken() {
        return new IdentityException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Tu sesión en la plataforma terminó. Elige tu cuenta de nuevo.");
    }

    public static IdentityException userNotFound() {
        return new IdentityException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "La cuenta no existe.");
    }

    public static IdentityException sessionExpired() {
        return new IdentityException(HttpStatus.CONFLICT, "SESSION_EXPIRED",
                "Tu sesión de LinkedIn expiró. Vuelve a conectar tu cuenta.");
    }

    public static IdentityException noSession() {
        return new IdentityException(HttpStatus.CONFLICT, "NOT_CONNECTED",
                "Esta cuenta no tiene una sesión de LinkedIn guardada. Conéctala de nuevo.");
    }

    public static IdentityException sessionBusy() {
        return new IdentityException(HttpStatus.CONFLICT, "SESSION_BUSY",
                "Hay una automatización usando esta cuenta. Intenta en unos minutos.");
    }

    public static IdentityException linkedinServiceUnavailable() {
        return new IdentityException(HttpStatus.SERVICE_UNAVAILABLE, "LINKEDIN_SERVICE_UNAVAILABLE",
                "No se pudo contactar a linkedin-service. Verifica que esté corriendo.");
    }
}
