package com.aplicafacil.aiconfig.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.aiconfig.domain.AiRequest;

/** Solicitudes de IA ({@code ai_request}). */
public interface AiRequestRepository {

    void save(AiRequest request);

    Optional<AiRequest> findOwned(UUID requestId, UUID userId);

    /** Las más recientes primero. */
    List<AiRequest> recent(UUID userId, int limit);

    /** Dos copias del mismo resultado (entrega al menos una vez) no se aplican a la vez. */
    Optional<AiRequest> findForUpdate(UUID requestId);

    /** Cierra como TIMED_OUT las pendientes vencidas. @return cuántas */
    int expire(Instant now);
}
