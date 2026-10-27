package com.aplicafacil.jobs.application;

/** Un caso de uso rechazó la petición. La capa api decide el código HTTP; application no conoce HTTP. */
public class JobSearchRejectedException extends RuntimeException {

    public enum Reason { UNSUPPORTED_PLATFORM, TOO_MANY_PENDING, NOT_FOUND }

    private final Reason reason;

    public JobSearchRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
