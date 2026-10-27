package com.aplicafacil.jobs.application;

import java.time.Duration;
import java.util.Objects;

/**
 * Cuántas veces se intenta una búsqueda ante fallos transitorios y cuánto se espera antes de cada reintento:
 * {@code base · 2^(n-2)} antes del intento n, con tope. La misma tabla configura las colas de espera de RabbitMQ.
 */
public record RetryPolicy(int maxAttempts, Duration base, Duration max) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts debe ser al menos 1");
        }
        Objects.requireNonNull(base);
        Objects.requireNonNull(max);
    }

    /** ¿Queda un intento después de {@code attempt}? */
    public boolean allowsAfter(int attempt) {
        return attempt < maxAttempts;
    }

    /** Espera antes del intento {@code attempt} (2 = primer reintento). */
    public Duration delayBefore(int attempt) {
        if (attempt < 2) {
            return Duration.ZERO;
        }
        long millis = base.toMillis() * (1L << Math.min(attempt - 2, 20));
        return Duration.ofMillis(Math.min(millis, max.toMillis()));
    }
}
