package com.aplicafacil.linkedin.application.services;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.linkedin.application.port.out.LinkedInConnectionRepository;
import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository;
import com.aplicafacil.linkedin.domain.ConnectionStatus;
import com.aplicafacil.linkedin.domain.LinkedInConnection;

/** Estado persistente de la conexión y avisos a quien lo esté escuchando por SSE. Las reglas viven en el dominio. */
@Service
public class ConnectionStateService {

    private final LinkedInConnectionRepository connections;
    private final LinkedInSessionRepository sessions;
    private final StatusBroadcaster broadcaster;
    private final Clock clock;

    public ConnectionStateService(LinkedInConnectionRepository connections, LinkedInSessionRepository sessions,
                                  StatusBroadcaster broadcaster, Clock clock) {
        this.connections = connections;
        this.sessions = sessions;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatusView status(UUID userId) {
        return connections.findById(userId).map(StatusView::from).orElse(StatusView.DISCONNECTED);
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> statuses(Collection<UUID> userIds) {
        Map<UUID, String> result = new HashMap<>();
        userIds.forEach(id -> result.put(id, ConnectionStatus.DISCONNECTED.name()));
        connections.findAllById(userIds).forEach(c -> result.put(c.userId(), c.status().name()));
        return result;
    }

    /** Reconexión de un usuario existente: mientras dure, su estado es CONNECTING. */
    @Transactional
    public ConnectionStatus markConnecting(UUID userId) {
        LinkedInConnection connection = getOrCreate(userId);
        ConnectionStatus previous = connection.status();
        connection.changeStatus(ConnectionStatus.CONNECTING, null, clock.instant());
        saveAndPublish(connection);
        return previous;
    }

    /** Vuelve al estado anterior si la reconexión falla o termina con otra cuenta. */
    @Transactional
    public void restore(UUID userId, ConnectionStatus previous, String error) {
        changeAndPublish(userId, c -> c.changeStatus(previous, error, clock.instant()));
    }

    @Transactional
    public void markConnected(UUID userId, String memberUrn) {
        LinkedInConnection connection = getOrCreate(userId);
        connections.releaseMember(memberUrn, userId);
        connection.connect(memberUrn, clock.instant());
        saveAndPublish(connection);
    }

    @Transactional
    public void markSynced(UUID userId, boolean complete) {
        change(userId, c -> c.synced(complete, clock.instant()));
    }

    @Transactional
    public void markSyncPending(UUID userId) {
        change(userId, c -> c.syncPending(clock.instant()));
    }

    /** La sesión venció o no se pudo descifrar: hay que reconectar. */
    @Transactional
    public void markExpired(UUID userId, String reason) {
        changeAndPublish(userId, c -> c.expire(reason, clock.instant()));
    }

    /** "Desconectar": borra la sesión y deja la conexión en DISCONNECTED. */
    @Transactional
    public void disconnect(UUID userId) {
        sessions.delete(userId);
        changeAndPublish(userId, c -> c.changeStatus(ConnectionStatus.DISCONNECTED, null, clock.instant()));
    }

    /** Borrado de la cuenta: sesión y conexión (la sesión cae en cascada). */
    @Transactional
    public void deleteAll(UUID userId) {
        sessions.delete(userId);
        connections.deleteById(userId);
        broadcaster.publish(userId, StatusView.DISCONNECTED);
    }

    private LinkedInConnection getOrCreate(UUID userId) {
        return connections.findById(userId)
                .orElseGet(() -> connections.save(LinkedInConnection.disconnected(userId, clock.instant())));
    }

    private void change(UUID userId, Consumer<LinkedInConnection> change) {
        connections.findById(userId).ifPresent(c -> {
            change.accept(c);
            connections.save(c);
        });
    }

    private void changeAndPublish(UUID userId, Consumer<LinkedInConnection> change) {
        connections.findById(userId).ifPresent(c -> {
            change.accept(c);
            saveAndPublish(c);
        });
    }

    private void saveAndPublish(LinkedInConnection connection) {
        connections.save(connection);
        broadcaster.publish(connection.userId(), StatusView.from(connection));
    }

    /** Lo que ve el front en {@code GET /api/linkedin/status}. */
    public record StatusView(ConnectionStatus status, String lastError, Instant connectedAt, Instant lastSyncAt) {

        static final StatusView DISCONNECTED = new StatusView(ConnectionStatus.DISCONNECTED, null, null, null);

        static StatusView from(LinkedInConnection c) {
            return new StatusView(c.status(), c.state().lastError(), c.timeline().connectedAt(),
                    c.timeline().lastSyncAt());
        }
    }
}
