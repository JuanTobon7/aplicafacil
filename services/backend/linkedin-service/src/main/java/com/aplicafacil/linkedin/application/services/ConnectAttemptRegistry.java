package com.aplicafacil.linkedin.application.services;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aplicafacil.linkedin.application.exception.ConnectException;

/**
 * Intentos de conexión en memoria. Solo puede haber uno activo a la vez: hay un único navegador visible y
 * el usuario solo puede iniciar sesión en una ventana a la vez.
 */
@Component
public class ConnectAttemptRegistry {

    private static final Duration RETENTION = Duration.ofMinutes(10);

    private final Map<UUID, ConnectAttempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public ConnectAttemptRegistry(Clock clock) {
        this.clock = clock;
    }

    public synchronized ConnectAttempt start(UUID expectedUserId) {
        if (attempts.values().stream().anyMatch(ConnectAttempt::isActive)) {
            throw ConnectException.alreadyConnecting();
        }
        ConnectAttempt attempt = new ConnectAttempt(UUID.randomUUID(), expectedUserId, clock.instant());
        attempts.put(attempt.id(), attempt);
        return attempt;
    }

    public Optional<ConnectAttempt> find(UUID attemptId) {
        return Optional.ofNullable(attempts.get(attemptId));
    }

    public ConnectAttempt get(UUID attemptId) {
        return find(attemptId).orElseThrow(ConnectException::attemptNotFound);
    }

    public SseEmitter subscribe(UUID attemptId) {
        return get(attemptId).subscribe();
    }

    /** Cancelar: el flujo lo detecta en su siguiente sondeo y cierra el navegador. */
    public void cancel(UUID attemptId) {
        get(attemptId).requestCancel();
    }

    @Scheduled(fixedDelay = 60_000)
    void purgeFinished() {
        var limit = clock.instant().minus(RETENTION);
        attempts.values().removeIf(a -> !a.isActive() && a.finishedAt() != null && a.finishedAt().isBefore(limit));
    }
}
