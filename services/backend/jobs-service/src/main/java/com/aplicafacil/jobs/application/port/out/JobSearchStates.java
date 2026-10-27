package com.aplicafacil.jobs.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.jobs.domain.JobSearch;

/**
 * Estado de cada búsqueda en la BD de jobs-service. Cada transición exige el estado esperado, así un mensaje repetido
 * o atrasado no pisa a otro.
 */
public interface JobSearchStates {

    /**
     * Empieza el intento {@code attempt}: QUEUED → RUNNING, o lo retoma si el mismo intento quedó RUNNING porque la
     * instancia que lo procesaba murió y RabbitMQ reentregó el mensaje.
     *
     * @return vacío si ya no corresponde (terminada, borrada o ya en un intento posterior)
     */
    Optional<JobSearch> start(UUID searchId, int attempt, Instant now);

    void complete(UUID searchId, int found, int created, Instant now);

    /** RUNNING → QUEUED, con el último error a la vista. @return {@code false} si ya no estaba RUNNING */
    boolean retryLater(UUID searchId, String code, String message, Instant nextAttemptAt);

    void fail(UUID searchId, String code, String message, Instant now);
}
