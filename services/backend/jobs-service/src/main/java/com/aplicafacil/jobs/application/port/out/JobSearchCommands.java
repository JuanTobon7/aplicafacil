package com.aplicafacil.jobs.application.port.out;

import java.util.UUID;

/**
 * Encola intentos de una búsqueda ({@code jobs.search.requested}, contracts/schemas/job-search-requested-command).
 * Se registran en la misma transacción que el cambio de estado (outbox) y se publican en RabbitMQ después.
 */
public interface JobSearchCommands {

    /** Primer intento, en cuanto se publique. */
    void requestFirstRun(UUID searchId, UUID userId);

    /** El intento {@code attempt}, después de su espera (colas de espera con TTL). */
    void scheduleRetry(UUID searchId, UUID userId, int attempt);
}
