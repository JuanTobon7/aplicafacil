package com.aplicafacil.profile.infrastructure.messaging;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.profile.application.port.out.AiRequests;
import com.aplicafacil.profile.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.aplicafacil.profile.infrastructure.persistence.entity.OutboxEventEntity;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AiRequests} sobre el outbox: {@code ai.request.submit} (contracts/schemas/ai-request-command.schema.json)
 * se guarda en la transacción en curso y {@link OutboxPublisher} lo publica en {@code aplicafacil.commands}.
 */
@Component
public class OutboxAiRequests implements AiRequests {

    static final int SCHEMA_VERSION = 1;

    private final SpringDataOutboxEventRepository outbox;
    private final JsonMapper json;

    public OutboxAiRequests(SpringDataOutboxEventRepository outbox, JsonMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Override
    public void submit(AiPrompt prompt) {
        AiRequestCommand command = new AiRequestCommand(SCHEMA_VERSION, prompt.requestId(), prompt.configId(),
                prompt.userId(), prompt.requestId(), prompt.messages(),
                new Parameters(null, prompt.temperature(), prompt.maxTokens(), null),
                prompt.submittedAt().toString(), prompt.deadline().toString());
        outbox.save(OutboxEventEntity.builder()
                .id(UUID.randomUUID())
                .aggregateType("ai_task")
                .aggregateId(prompt.requestId())
                .exchange(ProfileAiMessaging.COMMANDS_EXCHANGE)
                .eventType(ProfileAiMessaging.SUBMIT)
                .payload(json.writeValueAsString(command))
                .createdAt(prompt.submittedAt())
                .build());
    }

    /** El correlationId es el mismo requestId: una tarea = una solicitud. */
    record AiRequestCommand(int schemaVersion, UUID requestId, UUID configId, UUID userId, UUID correlationId,
                            List<Message> messages, Parameters parameters, String submittedAt, String deadline) {
    }

    record Parameters(String model, Double temperature, Integer maxTokens, Double topP) {
    }
}
