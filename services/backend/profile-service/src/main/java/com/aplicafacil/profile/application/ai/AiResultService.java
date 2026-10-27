package com.aplicafacil.profile.application.ai;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.profile.application.port.out.AiOutputReader.InvalidAiOutputException;
import com.aplicafacil.profile.application.port.out.AiTaskRepository;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;

/**
 * Aplica {@code ai.request.completed|failed}. Los resultados de solicitudes que no son de profile-service (las de
 * ai-config-service llegan a la misma routing key), los repetidos y los tardíos se ignoran.
 */
@Service
public class AiResultService {

    private static final Logger log = LoggerFactory.getLogger(AiResultService.class);

    private final AiTaskRepository tasks;
    private final Map<AiTaskKind, AiTaskCompletion> completions = new EnumMap<>(AiTaskKind.class);
    private final Clock clock;

    AiResultService(AiTaskRepository tasks, List<AiTaskCompletion> completions, Clock clock) {
        this.tasks = tasks;
        completions.forEach(c -> this.completions.put(c.kind(), c));
        this.clock = clock;
    }

    /** @return {@code true} si cerró una tarea; {@code false} si era ajeno, repetido o tardío */
    @Transactional
    public boolean apply(AiResult result) {
        AiTask task = tasks.findForUpdate(result.requestId()).orElse(null);
        if (task == null || task.isClosed()) {
            return false;
        }
        if (!task.belongsTo(result.userId(), result.configId())) {
            log.warn("Resultado de IA {} con userId/configId que no corresponden: se ignora", result.requestId());
            return false;
        }
        Instant now = clock.instant();
        if (result.completed()) {
            complete(task, result.text(), now);
        } else {
            task.fail(result.errorCode(), result.errorMessage(), now);
        }
        tasks.save(task);
        return true;
    }

    private void complete(AiTask task, String output, Instant now) {
        try {
            completions.get(task.kind()).apply(task, output, now);
            task.complete(now);
        } catch (InvalidAiOutputException | IllegalArgumentException e) {
            log.warn("Respuesta de IA ilegible para la tarea {} ({}): {}", task.id(), task.kind(), e.getMessage());
            task.fail("INVALID_OUTPUT", "La IA respondió algo que no se pudo interpretar. Prueba de nuevo u otro "
                    + "modelo.", now);
        }
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    @Transactional
    public void expire() {
        int expired = tasks.expire(clock.instant());
        if (expired > 0) {
            log.info("{} tareas de IA vencidas sin respuesta del mcp-gateway", expired);
        }
    }

    /** {@code ai.request.completed|failed} ya leído (contracts/schemas/ai-request-result-event.schema.json). */
    public record AiResult(UUID requestId, UUID userId, UUID configId, boolean completed, String text,
                           String errorCode, String errorMessage) {
    }
}
