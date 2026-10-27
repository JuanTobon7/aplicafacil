package com.aplicafacil.linkedin.api;

import java.util.Arrays;
import java.util.Optional;

import com.aplicafacil.linkedin.application.exception.NotConnectedException;
import com.aplicafacil.linkedin.application.exception.SessionBusyException;
import com.aplicafacil.linkedin.application.exception.SessionExpiredException;
import com.aplicafacil.linkedin.domain.LinkedInAction.SessionCheck;

/**
 * Respuesta de {@code /internal/sessions/{userId}/validate}. Es el contrato con identity-service
 * ({@code LinkedInSessionClient.SessionCheck}): cada valor sale de un resultado de la validación o de un fallo conocido.
 */
enum SessionValidationResult {

    VALID(SessionCheck.VALID, null),
    EXPIRED(SessionCheck.EXPIRED, SessionExpiredException.class),
    NOT_CONNECTED(null, NotConnectedException.class),
    BUSY(null, SessionBusyException.class);

    private final SessionCheck check;
    private final Class<? extends RuntimeException> failure;

    SessionValidationResult(SessionCheck check, Class<? extends RuntimeException> failure) {
        this.check = check;
        this.failure = failure;
    }

    static SessionValidationResult of(SessionCheck check) {
        return Arrays.stream(values())
                .filter(r -> r.check == check)
                .findFirst()
                .orElseThrow();
    }

    /** Vacío si el fallo no es uno de los esperados. */
    static Optional<SessionValidationResult> ofFailure(Throwable cause) {
        return Arrays.stream(values())
                .filter(r -> r.failure != null && r.failure.isInstance(cause))
                .findFirst();
    }
}
