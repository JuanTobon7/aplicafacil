package com.aplicafacil.jobs.application.port.out;

/**
 * Fallo de un portal con un código estable (NOT_CONNECTED, SESSION_EXPIRED, SESSION_BUSY, SEARCH_FAILED,
 * PORTAL_UNAVAILABLE…). {@code retryable} decide si el worker vuelve a encolar la búsqueda.
 */
public class JobPortalException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public JobPortalException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public JobPortalException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
