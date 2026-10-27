package com.aplicafacil.profile.application.ai;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.AiRequests;
import com.aplicafacil.profile.application.port.out.AiRequests.AiPrompt;
import com.aplicafacil.profile.application.port.out.AiRequests.Message;
import com.aplicafacil.profile.application.port.out.AiTaskRepository;
import com.aplicafacil.profile.domain.AiTask;
import com.aplicafacil.profile.domain.AiTaskKind;

/** Crea la tarea y deja {@code ai.request.submit} en el outbox, en la transacción de quien lo llama. */
@Component
class AiTaskSubmitter {

    /** Respuestas deterministas: es extracción y clasificación, no redacción creativa. */
    private static final double TEMPERATURE = 0.1;
    private static final int MAX_TOKENS = 4_000;

    private final AiTaskRepository tasks;
    private final AiRequests requests;
    private final ProfileAiPolicy policy;
    private final Clock clock;

    AiTaskSubmitter(AiTaskRepository tasks, AiRequests requests, ProfileAiPolicy policy, Clock clock) {
        this.tasks = tasks;
        this.requests = requests;
        this.policy = policy;
        this.clock = clock;
    }

    AiTask submit(Request request) {
        Instant now = clock.instant();
        AiTask task = AiTask.submit(request.userId(), request.configId(), request.kind(), request.fileName(),
                new AiTask.Window(now, now.plus(policy.timeout())));
        tasks.save(task);
        requests.submit(new AiPrompt(task.id(), task.userId(), task.configId(), request.messages(), now,
                now.plus(policy.timeout()), TEMPERATURE, MAX_TOKENS));
        return task;
    }

    /** @param fileName solo para mostrarlo ({@code null} si no viene de un archivo) */
    record Request(UUID userId, UUID configId, AiTaskKind kind, String fileName, List<Message> messages) {
    }
}
