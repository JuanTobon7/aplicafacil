package com.aplicafacil.profile.application.exception;

/** Un pedido que el caso de uso no acepta. El código HTTP lo elige {@code api}. */
public class ProfileRejectedException extends RuntimeException {

    private final Reason reason;

    public ProfileRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        PROFILE_NOT_FOUND,
        TASK_NOT_FOUND,
        CV_UNSUPPORTED,
        CV_UNREADABLE,
        AI_TASK_IN_PROGRESS,
        INVALID_REQUEST
    }
}
