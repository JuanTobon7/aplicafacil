package com.aplicafacil.computrabajo.application.services;

import java.time.Instant;
import java.util.UUID;

import com.aplicafacil.computrabajo.domain.ConnectStep;

/** Un intento de conexión. Vive solo en memoria; el front lo sigue por polling. */
public class ConnectAttempt {

    public enum State { CONNECTING, CONNECTED, FAILED, CANCELLED }

    private final UUID id;
    private final UUID userId;
    private final Instant startedAt;

    private volatile State state = State.CONNECTING;
    private volatile ConnectStep step = ConnectStep.OPENING_BROWSER;
    private volatile String errorCode;
    private volatile String errorMessage;
    private volatile String candidateName;
    private volatile boolean cancelRequested;
    private volatile Instant finishedAt;

    ConnectAttempt(UUID id, UUID userId, Instant startedAt) {
        this.id = id;
        this.userId = userId;
        this.startedAt = startedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public boolean isActive() {
        return state == State.CONNECTING;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    void requestCancel() {
        cancelRequested = true;
    }

    public void step(ConnectStep next) {
        this.step = next;
    }

    void succeed(String name, Instant now) {
        this.candidateName = name;
        finish(State.CONNECTED, now);
    }

    void fail(String code, String message, Instant now) {
        this.errorCode = code;
        this.errorMessage = message;
        finish(cancelRequested ? State.CANCELLED : State.FAILED, now);
    }

    private void finish(State finalState, Instant now) {
        this.finishedAt = now;
        this.state = finalState;
    }

    public AttemptView view() {
        boolean connecting = state == State.CONNECTING;
        return new AttemptView(id, state, connecting ? step : null, connecting ? step.label() : null,
                errorCode, errorMessage, candidateName, startedAt);
    }

    /** contracts/openapi/computrabajo-api.yaml, {@code ConnectAttempt}. */
    public record AttemptView(UUID attemptId, State state, ConnectStep step, String stepLabel, String errorCode,
                              String errorMessage, String candidateName, Instant startedAt) {
    }
}
