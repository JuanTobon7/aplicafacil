package com.aplicafacil.computrabajo.application.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.aplicafacil.computrabajo.application.exception.ConnectException;

/**
 * Intentos de conexión en memoria. Solo puede haber uno activo a la vez: hay un único navegador visible. Cada persona
 * solo ve los suyos.
 */
@Component
public class ConnectAttemptRegistry {

    private static final Duration RETENTION = Duration.ofMinutes(10);

    private final Map<UUID, ConnectAttempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public ConnectAttemptRegistry(Clock clock) {
        this.clock = clock;
    }

    public synchronized ConnectAttempt start(UUID userId) {
        if (attempts.values().stream().anyMatch(ConnectAttempt::isActive)) {
            throw ConnectException.alreadyConnecting();
        }
        ConnectAttempt attempt = new ConnectAttempt(UUID.randomUUID(), userId, clock.instant());
        attempts.put(attempt.id(), attempt);
        return attempt;
    }

    /** El intento de otra persona se trata como inexistente. */
    public ConnectAttempt get(UUID userId, UUID attemptId) {
        ConnectAttempt attempt = attempts.get(attemptId);
        if (attempt == null || !attempt.userId().equals(userId)) {
            throw ConnectException.attemptNotFound();
        }
        return attempt;
    }

    @Scheduled(fixedDelay = 60_000)
    void purgeFinished() {
        Instant limit = clock.instant().minus(RETENTION);
        attempts.values().removeIf(a -> !a.isActive() && a.finishedAt() != null && a.finishedAt().isBefore(limit));
    }
}
