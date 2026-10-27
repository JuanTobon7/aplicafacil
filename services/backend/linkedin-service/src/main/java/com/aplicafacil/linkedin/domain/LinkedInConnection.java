package com.aplicafacil.linkedin.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Conexión de un usuario con LinkedIn. Es la experta en sus transiciones de estado (GRASP): los servicios le piden
 * el cambio y ella sabe qué campos tocar.
 */
public final class LinkedInConnection {

    public static final String PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE";
    public static final String PROFILE_SYNC_PENDING = "PROFILE_SYNC_PENDING";

    private final UUID userId;
    private final Instant createdAt;
    private ConnectionStatus status;
    private ConnectionType connectionType;
    private String memberUrn;
    private String lastError;
    private Instant connectedAt;
    private Instant lastSyncAt;
    private Instant expiredAt;
    private Instant updatedAt;

    private LinkedInConnection(UUID userId, Instant createdAt) {
        this.userId = Objects.requireNonNull(userId);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** Un usuario que aún no se conectó. */
    public static LinkedInConnection disconnected(UUID userId, Instant now) {
        LinkedInConnection c = new LinkedInConnection(userId, now);
        c.status = ConnectionStatus.DISCONNECTED;
        c.connectionType = ConnectionType.SESSION;
        c.updatedAt = now;
        return c;
    }

    /** Reconstruye una conexión guardada (lo usa el adaptador de persistencia). */
    public static LinkedInConnection restore(UUID userId, Instant createdAt, State state, Timeline timeline) {
        LinkedInConnection c = new LinkedInConnection(userId, createdAt);
        c.status = state.status();
        c.connectionType = state.connectionType();
        c.memberUrn = state.memberUrn();
        c.lastError = state.lastError();
        c.connectedAt = timeline.connectedAt();
        c.lastSyncAt = timeline.lastSyncAt();
        c.expiredAt = timeline.expiredAt();
        c.updatedAt = timeline.updatedAt();
        return c;
    }

    public void changeStatus(ConnectionStatus newStatus, String error, Instant now) {
        status = Objects.requireNonNull(newStatus);
        lastError = error;
        updatedAt = now;
    }

    public void connect(String member, Instant now) {
        memberUrn = Objects.requireNonNull(member);
        connectionType = ConnectionType.SESSION;
        connectedAt = now;
        expiredAt = null;
        changeStatus(ConnectionStatus.CONNECTED, null, now);
    }

    public void synced(boolean complete, Instant now) {
        lastSyncAt = now;
        lastError = complete ? null : PROFILE_INCOMPLETE;
        updatedAt = now;
    }

    public void syncPending(Instant now) {
        lastError = PROFILE_SYNC_PENDING;
        updatedAt = now;
    }

    public void expire(String reason, Instant now) {
        expiredAt = now;
        changeStatus(ConnectionStatus.EXPIRED, reason, now);
    }

    public UUID userId() {
        return userId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ConnectionStatus status() {
        return status;
    }

    public State state() {
        return new State(status, connectionType, memberUrn, lastError);
    }

    public Timeline timeline() {
        return new Timeline(connectedAt, lastSyncAt, expiredAt, updatedAt);
    }

    /** Estado actual; {@code lastError} es un código, nunca datos de la sesión. */
    public record State(ConnectionStatus status, ConnectionType connectionType, String memberUrn, String lastError) {
    }

    public record Timeline(Instant connectedAt, Instant lastSyncAt, Instant expiredAt, Instant updatedAt) {
    }
}
