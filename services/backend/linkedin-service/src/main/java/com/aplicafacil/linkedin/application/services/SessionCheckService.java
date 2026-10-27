package com.aplicafacil.linkedin.application.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.aplicafacil.linkedin.application.exception.NotConnectedException;
import com.aplicafacil.linkedin.application.exception.SessionBusyException;
import com.aplicafacil.linkedin.application.exception.SessionExpiredException;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionProvider;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository;
import com.aplicafacil.linkedin.domain.LinkedInAction.SessionCheck;
import com.aplicafacil.linkedin.domain.LinkedInAction.ValidateSession;

/**
 * Comprueba que la sesión guardada siga viva, para que el estado que ve la persona sea real y, si venció, se le pida
 * iniciar sesión de nuevo. Una validación reciente se reusa: abrir la app varias veces no abre un navegador cada vez.
 */
@Service
public class SessionCheckService {

    /** Una validación más reciente que esto se da por buena. */
    static final Duration FRESH = Duration.ofMinutes(10);
    private static final long TIMEOUT_SECONDS = 80;
    private static final Logger log = LoggerFactory.getLogger(SessionCheckService.class);

    private final LinkedInSessionProvider provider;
    private final LinkedInSessionRepository sessions;
    private final Clock clock;

    public SessionCheckService(LinkedInSessionProvider provider, LinkedInSessionRepository sessions, Clock clock) {
        this.provider = provider;
        this.sessions = sessions;
        this.clock = clock;
    }

    public Validity check(UUID userId) throws InterruptedException {
        if (!sessions.exists(userId)) {
            return Validity.NOT_CONNECTED;
        }
        Instant freshSince = clock.instant().minus(FRESH);
        if (sessions.lastValidatedAt(userId).filter(at -> at.isAfter(freshSince)).isPresent()) {
            return Validity.VALID;
        }
        try {
            SessionCheck check = provider.execute(userId, new ValidateSession()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return check == SessionCheck.VALID ? Validity.VALID : Validity.EXPIRED;
        } catch (ExecutionException e) {
            return fromFailure(userId, e.getCause());
        } catch (TimeoutException e) {
            log.warn("La comprobación de la sesión de {} tardó más de {} s", userId, TIMEOUT_SECONDS);
            return Validity.UNAVAILABLE;
        }
    }

    private static Validity fromFailure(UUID userId, Throwable cause) {
        return switch (cause) {
            case SessionExpiredException e -> Validity.EXPIRED;
            case NotConnectedException e -> Validity.NOT_CONNECTED;
            case SessionBusyException e -> Validity.BUSY;
            default -> {
                log.warn("No se pudo comprobar la sesión de {}: {}", userId, cause == null ? null : cause.getMessage());
                yield Validity.UNAVAILABLE;
            }
        };
    }

    /** contracts/openapi/linkedin-api.yaml, {@code SessionCheck.result}. */
    public enum Validity { VALID, EXPIRED, NOT_CONNECTED, BUSY, UNAVAILABLE }
}
