package com.aplicafacil.aiconfig.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Solicitud de IA asíncrona. Se cierra una sola vez: un resultado repetido o tardío (ya vencida) no la cambia.
 * El prompt no se guarda: viaja solo en el comando a RabbitMQ.
 */
public final class AiRequest {

    private static final int MAX_MESSAGE = 500;

    private final UUID id;
    private final UUID userId;
    private final UUID configId;
    private final UUID correlationId;
    private final Instant createdAt;
    private final Instant deadline;
    private AiRequestStatus status;
    private Output output;
    private Failure failure;
    private Integer latencyMs;
    private Instant completedAt;

    private AiRequest(Snapshot s) {
        this.id = Objects.requireNonNull(s.id());
        this.userId = Objects.requireNonNull(s.userId());
        this.configId = Objects.requireNonNull(s.configId());
        this.correlationId = s.correlationId();
        this.createdAt = s.createdAt();
        this.deadline = s.deadline();
        this.status = s.status();
        this.output = s.output();
        this.failure = s.failure();
        this.latencyMs = s.latencyMs();
        this.completedAt = s.completedAt();
    }

    public static AiRequest submit(UUID userId, UUID configId, Instant now, Instant deadline) {
        return new AiRequest(new Snapshot(UUID.randomUUID(), userId, configId, UUID.randomUUID(),
                AiRequestStatus.PENDING, null, null, null, now, deadline, null));
    }

    /** Reconstruye una solicitud guardada (lo usa el adaptador de persistencia). */
    public static AiRequest restore(Snapshot snapshot) {
        return new AiRequest(snapshot);
    }

    /** El resultado debe venir del mismo usuario y configuración que la pidieron. */
    public boolean belongsTo(UUID user, UUID config) {
        return userId.equals(user) && configId.equals(config);
    }

    public boolean isClosed() {
        return status.isFinal();
    }

    public void complete(Output result, Long latency, Instant now) {
        close(AiRequestStatus.COMPLETED, latency, now);
        output = result;
    }

    public void fail(String code, String message, Long latency, Instant now) {
        close(AiRequestStatus.FAILED, latency, now);
        failure = new Failure(code, message == null || message.length() <= MAX_MESSAGE
                ? message : message.substring(0, MAX_MESSAGE));
    }

    private void close(AiRequestStatus finalStatus, Long latency, Instant now) {
        if (isClosed()) {
            throw new IllegalStateException("La solicitud " + id + " ya está cerrada");
        }
        status = finalStatus;
        latencyMs = latency == null ? null : Math.toIntExact(latency);
        completedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID configId() {
        return configId;
    }

    public UUID correlationId() {
        return correlationId;
    }

    public Snapshot snapshot() {
        return new Snapshot(id, userId, configId, correlationId, status, output, failure, latencyMs, createdAt,
                deadline, completedAt);
    }

    public record Output(String text, String model, String finishReason, Integer inputTokens, Integer outputTokens) {
    }

    public record Failure(String code, String message) {
    }

    /** Estado completo, para guardar y reconstruir. */
    public record Snapshot(UUID id, UUID userId, UUID configId, UUID correlationId, AiRequestStatus status,
                           Output output, Failure failure, Integer latencyMs, Instant createdAt, Instant deadline,
                           Instant completedAt) {
    }
}
