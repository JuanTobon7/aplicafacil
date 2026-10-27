package com.aplicafacil.computrabajo.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * La conexión de una persona con su cuenta de candidato. El storageState no está aquí: es secreto y solo lo maneja
 * el repositorio, cifrado.
 *
 * @param candidateName   nombre que muestra la cabecera de Computrabajo; {@code null} si no se pudo leer
 * @param lastValidatedAt última vez que se comprobó que la sesión sigue viva (iniciarla también cuenta)
 */
public record ComputrabajoConnection(UUID userId, ConnectionStatus status, String candidateName, Instant connectedAt,
                                     Instant lastValidatedAt) {

    public ComputrabajoConnection {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(connectedAt, "connectedAt");
        candidateName = candidateName == null || candidateName.isBlank() ? null : candidateName.strip();
    }

    /** Recién iniciada la sesión: conectada y validada en este momento. */
    public static ComputrabajoConnection connected(UUID userId, String candidateName, Instant now) {
        return new ComputrabajoConnection(userId, ConnectionStatus.CONNECTED, candidateName, now, now);
    }

    public ComputrabajoConnection validated(Instant now) {
        return new ComputrabajoConnection(userId, ConnectionStatus.CONNECTED, candidateName, connectedAt, now);
    }

    public ComputrabajoConnection expired() {
        return new ComputrabajoConnection(userId, ConnectionStatus.EXPIRED, candidateName, connectedAt, lastValidatedAt);
    }

    /** Conectada y comprobada después de {@code since}: no hace falta abrir el navegador para validarla. */
    public boolean validatedAfter(Instant since) {
        return status == ConnectionStatus.CONNECTED && lastValidatedAt != null && lastValidatedAt.isAfter(since);
    }
}
