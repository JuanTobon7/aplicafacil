package com.aplicafacil.profile.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una solicitud de IA que hizo profile-service (su id es el {@code requestId} de {@code ai.request.submit}). Se cierra
 * una sola vez: un resultado repetido o tardío no la reabre (entrega al menos una vez).
 */
public final class AiTask {

    private final UUID id;
    private final UUID userId;
    private final UUID configId;
    private final AiTaskKind kind;
    private final String fileName;
    private final Instant createdAt;
    private final Instant deadline;
    private AiTaskStatus status;
    private String errorCode;
    private String errorMessage;
    private Instant completedAt;

    private AiTask(Origin origin, AiTaskKind kind, String fileName, Instant createdAt, Instant deadline) {
        this.id = Objects.requireNonNull(origin.id());
        this.userId = Objects.requireNonNull(origin.userId());
        this.configId = Objects.requireNonNull(origin.configId());
        this.kind = Objects.requireNonNull(kind);
        this.fileName = Text.clean(fileName, 255);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.deadline = Objects.requireNonNull(deadline);
    }

    public static AiTask submit(UUID userId, UUID configId, AiTaskKind kind, String fileName, Window window) {
        AiTask task = new AiTask(new Origin(UUID.randomUUID(), userId, configId), kind, fileName, window.createdAt(),
                window.deadline());
        task.status = AiTaskStatus.PENDING;
        return task;
    }

    /** Reconstruye una tarea guardada (lo usa el adaptador de persistencia). */
    public static AiTask restore(Origin origin, AiTaskKind kind, String fileName, Window window, Outcome outcome) {
        AiTask task = new AiTask(origin, kind, fileName, window.createdAt(), window.deadline());
        task.status = outcome.status();
        task.errorCode = outcome.errorCode();
        task.errorMessage = outcome.errorMessage();
        task.completedAt = outcome.completedAt();
        return task;
    }

    public boolean isClosed() {
        return status != AiTaskStatus.PENDING;
    }

    /** El resultado trae el usuario y la configuración de la solicitud: si no coinciden, no es de esta tarea. */
    public boolean belongsTo(UUID resultUserId, UUID resultConfigId) {
        return userId.equals(resultUserId) && configId.equals(resultConfigId);
    }

    public void complete(Instant now) {
        close(AiTaskStatus.COMPLETED, null, null, now);
    }

    public void fail(String code, String message, Instant now) {
        close(AiTaskStatus.FAILED, Text.clean(code, 64), Text.clean(message, 500), now);
    }

    private void close(AiTaskStatus closedAs, String code, String message, Instant now) {
        if (isClosed()) {
            throw new IllegalStateException("La tarea " + id + " ya estaba cerrada");
        }
        status = closedAs;
        errorCode = code;
        errorMessage = message;
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

    public AiTaskKind kind() {
        return kind;
    }

    public String fileName() {
        return fileName;
    }

    public Window window() {
        return new Window(createdAt, deadline);
    }

    public Outcome outcome() {
        return new Outcome(status, errorCode, errorMessage, completedAt);
    }

    /** Quién la pidió y con qué proveedor. */
    public record Origin(UUID id, UUID userId, UUID configId) {
    }

    /** Desde cuándo y hasta cuándo se espera el resultado. */
    public record Window(Instant createdAt, Instant deadline) {
    }

    /** Cómo terminó ({@code PENDING} mientras no llega el resultado). */
    public record Outcome(AiTaskStatus status, String errorCode, String errorMessage, Instant completedAt) {
    }
}
