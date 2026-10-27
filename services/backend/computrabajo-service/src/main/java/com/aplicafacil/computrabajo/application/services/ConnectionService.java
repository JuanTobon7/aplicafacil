package com.aplicafacil.computrabajo.application.services;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.aplicafacil.computrabajo.application.port.out.ConnectionRepository;

/** Estado de la conexión y desconexión (a pedido de la persona o porque se borró la cuenta). */
@Service
public class ConnectionService {

    private final ConnectionRepository connections;

    public ConnectionService(ConnectionRepository connections) {
        this.connections = connections;
    }

    public StatusView status(UUID userId) {
        return connections.find(userId)
                .map(c -> new StatusView(c.status().name(), c.candidateName(), c.connectedAt(), c.lastValidatedAt()))
                .orElse(StatusView.NOT_CONNECTED);
    }

    /** Idempotente: también la usa {@code user.deleted}. */
    public void disconnect(UUID userId) {
        connections.delete(userId);
    }

    /** contracts/openapi/computrabajo-api.yaml, {@code ConnectionStatus}. */
    public record StatusView(String status, String candidateName, Instant connectedAt, Instant lastValidatedAt) {
        static final StatusView NOT_CONNECTED = new StatusView("NOT_CONNECTED", null, null, null);
    }
}
