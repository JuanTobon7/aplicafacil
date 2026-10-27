package com.aplicafacil.jobs.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Una búsqueda de vacantes pedida por un usuario: es la tarea que procesa el worker.
 *
 * @param attempts intentos ya empezados (incluido el actual mientras está RUNNING)
 */
public record JobSearch(
        UUID id,
        UUID userId,
        JobPlatform platform,
        SearchCriteria criteria,
        JobSearchStatus status,
        int attempts,
        Instant requestedAt) {
}
