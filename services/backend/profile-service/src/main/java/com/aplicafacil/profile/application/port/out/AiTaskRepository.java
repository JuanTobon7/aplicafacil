package com.aplicafacil.profile.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.profile.domain.AiTask;

/** Las solicitudes de IA de profile-service. */
public interface AiTaskRepository {

    AiTask save(AiTask task);

    Optional<AiTask> findOwned(UUID taskId, UUID userId);

    /** Bloquea la fila: dos copias del mismo resultado no se aplican a la vez. */
    Optional<AiTask> findForUpdate(UUID taskId);

    List<AiTask> recent(UUID userId, int limit);

    boolean hasPending(UUID userId);

    /** Cierra como TIMED_OUT las pendientes vencidas. @return cuántas */
    int expire(Instant now);

    void deleteByUserId(UUID userId);
}
