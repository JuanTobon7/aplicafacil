package com.aplicafacil.linkedin.application.services;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aplicafacil.linkedin.domain.ConnectStep;

/**
 * Un intento de conexión. Vive solo en memoria: al empezar todavía no existe un usuario (conectar = iniciar
 * sesión). El front lo sigue por SSE ({@code /linkedin/connect/{id}/events}) o por polling.
 */
public class ConnectAttempt {

    public enum State { CONNECTING, CONNECTED, FAILED, CANCELLED }

    private final UUID id;
    private final UUID expectedUserId;
    private final Instant startedAt;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private volatile State state = State.CONNECTING;
    private volatile ConnectStep step = ConnectStep.OPENING_BROWSER;
    private volatile String errorCode;
    private volatile String errorMessage;
    private volatile UUID userId;
    private volatile String loginCode;
    private volatile String outcome;
    private volatile boolean profileComplete;
    private volatile boolean cancelRequested;
    private volatile Instant finishedAt;

    ConnectAttempt(UUID id, UUID expectedUserId, Instant startedAt) {
        this.id = id;
        this.expectedUserId = expectedUserId;
        this.startedAt = startedAt;
    }

    public UUID id() {
        return id;
    }

    /** Usuario que pidió reconectar, o {@code null} en un primer ingreso / "Agregar otra cuenta". */
    public UUID expectedUserId() {
        return expectedUserId;
    }

    public boolean isActive() {
        return state == State.CONNECTING;
    }

    public boolean cancelRequested() {
        return cancelRequested;
    }

    public Instant finishedAt() {
        return finishedAt;
    }

    void requestCancel() {
        cancelRequested = true;
    }

    public void step(ConnectStep next) {
        this.step = next;
        broadcast();
    }

    public void succeed(UUID userId, String loginCode, String outcome, boolean profileComplete) {
        this.userId = userId;
        this.loginCode = loginCode;
        this.outcome = outcome;
        this.profileComplete = profileComplete;
        finish(State.CONNECTED);
    }

    public void fail(String code, String message) {
        this.errorCode = code;
        this.errorMessage = message;
        finish(cancelRequested ? State.CANCELLED : State.FAILED);
    }

    private void finish(State finalState) {
        this.state = finalState;
        this.finishedAt = Instant.now();
        broadcast();
        emitters.forEach(SseEmitter::complete);
        emitters.clear();
    }

    SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        if (!isActive()) {
            send(emitter);
            emitter.complete();
            return emitter;
        }
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        send(emitter);
        return emitter;
    }

    public AttemptView view() {
        boolean switched = expectedUserId != null && userId != null && !expectedUserId.equals(userId);
        return new AttemptView(id, state, state == State.CONNECTING ? step : null,
                state == State.CONNECTING ? step.label() : null,
                errorCode, errorMessage, userId,
                // El código solo se entrega cuando terminó bien; es de un solo uso y vence en segundos
                state == State.CONNECTED ? loginCode : null,
                outcome, profileComplete, switched, startedAt);
    }

    private void broadcast() {
        emitters.forEach(this::send);
    }

    private void send(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("attempt").data(view()));
        } catch (IOException | IllegalStateException e) {
            emitters.remove(emitter);
        }
    }

    /**
     * @param switchedAccount en una reconexión, inició sesión otro miembro: se abrió esa otra cuenta en lugar
     *                        de mezclar datos
     */
    public record AttemptView(UUID attemptId, State state, ConnectStep step, String stepLabel, String errorCode,
                              String errorMessage, UUID userId, String loginCode, String outcome,
                              boolean profileComplete, boolean switchedAccount, Instant startedAt) {
    }
}
