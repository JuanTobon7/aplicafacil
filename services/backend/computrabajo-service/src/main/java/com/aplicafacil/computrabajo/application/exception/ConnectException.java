package com.aplicafacil.computrabajo.application.exception;

/**
 * Error del flujo de conexión con un {@code code} estable que el front traduce a un mensaje y una acción. El estado
 * HTTP lo decide la api.
 */
public class ConnectException extends RuntimeException {

    private final String code;

    public ConnectException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ConnectException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ConnectException alreadyConnecting() {
        return new ConnectException("ALREADY_CONNECTING",
                "Ya hay una ventana abierta para iniciar sesión. Termina o cancela ese intento.");
    }

    public static ConnectException attemptNotFound() {
        return new ConnectException("ATTEMPT_NOT_FOUND", "El intento de conexión no existe o ya venció.");
    }

    public static ConnectException browserNotReady(String detail) {
        return new ConnectException("BROWSER_NOT_READY", detail);
    }

    public static ConnectException scraperUnavailable(Throwable cause) {
        return new ConnectException("SCRAPER_UNAVAILABLE",
                "El servicio que maneja el navegador (scraper) no responde. Verifica que esté corriendo.", cause);
    }

    public static ConnectException browserFailed() {
        return new ConnectException("BROWSER_FAILED", "No se pudo completar la conexión con Computrabajo.");
    }

    public static ConnectException databaseUnavailable(Throwable cause) {
        return new ConnectException("DATABASE_UNAVAILABLE",
                "La base de datos no está disponible. Levanta Docker con: docker compose up -d (en infra/).", cause);
    }
}
