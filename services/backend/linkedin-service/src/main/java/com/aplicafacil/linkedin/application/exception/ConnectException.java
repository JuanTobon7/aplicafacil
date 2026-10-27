package com.aplicafacil.linkedin.application.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Error del flujo de conexión con un {@code code} estable que el front traduce a un mensaje y una acción
 * (reintentar, levantar Docker, etc.).
 */
public class ConnectException extends RuntimeException {

    /** Código del scraper → estado HTTP para el front. */
    private static final Map<String, HttpStatus> SCRAPER_STATUS = Map.of(
            "LOGIN_TIMEOUT", HttpStatus.REQUEST_TIMEOUT,
            "ALREADY_CONNECTING", HttpStatus.CONFLICT,
            "WINDOW_CLOSED", HttpStatus.CONFLICT,
            "CANCELLED", HttpStatus.CONFLICT,
            "BROWSER_NOT_READY", HttpStatus.SERVICE_UNAVAILABLE,
            "IDENTITY_UNREADABLE", HttpStatus.BAD_GATEWAY,
            "SEARCH_FAILED", HttpStatus.BAD_GATEWAY,
            "INVALID_REQUEST", HttpStatus.BAD_REQUEST,
            "SESSION_REQUIRED", HttpStatus.BAD_REQUEST);

    private final HttpStatus status;
    private final String code;

    public ConnectException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public ConnectException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ConnectException alreadyConnecting() {
        return new ConnectException(HttpStatus.CONFLICT, "ALREADY_CONNECTING",
                "Ya hay una ventana de LinkedIn abierta para conectar una cuenta. Termina o cancela ese intento.");
    }

    public static ConnectException attemptNotFound() {
        return new ConnectException(HttpStatus.NOT_FOUND, "ATTEMPT_NOT_FOUND", "El intento de conexión no existe o ya venció.");
    }

    public static ConnectException windowClosed() {
        return new ConnectException(HttpStatus.CONFLICT, "WINDOW_CLOSED",
                "Cerraste la ventana de LinkedIn antes de terminar de iniciar sesión.");
    }

    public static ConnectException cancelled() {
        return new ConnectException(HttpStatus.CONFLICT, "CANCELLED", "Cancelaste la conexión.");
    }

    public static ConnectException loginTimeout(long minutes) {
        return new ConnectException(HttpStatus.REQUEST_TIMEOUT, "LOGIN_TIMEOUT",
                "No se completó el inicio de sesión en " + minutes + " minutos. Inténtalo de nuevo.");
    }

    public static ConnectException browserNotReady(String detail) {
        return new ConnectException(HttpStatus.SERVICE_UNAVAILABLE, "BROWSER_NOT_READY",
                "El navegador para conectar LinkedIn aún no está listo: " + detail);
    }

    public static ConnectException browserFailed(Throwable cause) {
        return new ConnectException(HttpStatus.INTERNAL_SERVER_ERROR, "BROWSER_FAILED",
                "No se pudo abrir el navegador. Revisa los logs de linkedin-service.", cause);
    }

    public static ConnectException identityUnreadable(Throwable cause) {
        return new ConnectException(HttpStatus.BAD_GATEWAY, "IDENTITY_UNREADABLE",
                "Iniciaste sesión, pero no pudimos leer tu identidad de LinkedIn. Inténtalo de nuevo.", cause);
    }

    public static ConnectException identityServiceUnavailable(Throwable cause) {
        return new ConnectException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_SERVICE_UNAVAILABLE",
                "No se pudo crear tu cuenta porque identity-service no responde. Verifica que esté corriendo.", cause);
    }

    public static ConnectException scraperUnavailable(Throwable cause) {
        return new ConnectException(HttpStatus.SERVICE_UNAVAILABLE, "SCRAPER_UNAVAILABLE",
                "El servicio que maneja el navegador (scraper) no responde. Verifica que esté corriendo.", cause);
    }

    /** Error informado por el scraper (WINDOW_CLOSED, LOGIN_TIMEOUT, CANCELLED, IDENTITY_UNREADABLE…). */
    public static ConnectException fromScraper(String code, String message) {
        return new ConnectException(SCRAPER_STATUS.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR), code, message);
    }

    public static ConnectException databaseUnavailable(Throwable cause) {
        return new ConnectException(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE",
                "La base de datos no está disponible. Levanta Docker con: docker compose up -d (en infra/).", cause);
    }
}
