package com.aplicafacil.aiconfig.infrastructure.messaging;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.aiconfig.application.port.out.AiEventOutbox;
import com.aplicafacil.aiconfig.domain.AiRequest;
import com.aplicafacil.aiconfig.domain.ModelParameters;
import com.aplicafacil.aiconfig.domain.ProviderConfig;
import com.aplicafacil.aiconfig.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.aplicafacil.aiconfig.infrastructure.persistence.entity.OutboxEventEntity;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@link AiEventOutbox} sobre la tabla outbox: arma los mensajes según contracts/schemas/ai-*.schema.json y
 * {@link OutboxPublisher} los publica. Ningún record de aquí tiene un campo para la API key: es imposible
 * publicarla por error.
 */
@Component
public class AiMessages implements AiEventOutbox {

    public static final int SCHEMA_VERSION = 1;

    public static final String CONFIG_CREATED = "ai.provider-config.created";
    public static final String CONFIG_UPDATED = "ai.provider-config.updated";
    public static final String CONFIG_DISABLED = "ai.provider-config.disabled";
    public static final String CONFIG_DELETED = "ai.provider-config.deleted";
    public static final String CREDENTIAL_ROTATED = "ai.provider-config.credential-rotated";
    public static final String PROVIDER_TEST = "ai.provider.test";
    public static final String REQUEST_SUBMIT = "ai.request.submit";

    private static final String PROVIDER_CONFIG = "provider_config";

    private final SpringDataOutboxEventRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    public AiMessages(SpringDataOutboxEventRepository outbox, JsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    /** {@code ai.provider-config.*} (ai-provider-config-changed-event.schema.json). */
    @Override
    public void configChanged(ConfigChange change, ProviderConfig config) {
        String eventType = routingKey(change);
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        ProviderConfig.Snapshot c = config.snapshot();
        ConfigChangedEvent payload = new ConfigChangedEvent(SCHEMA_VERSION, eventId, eventType, c.id(), c.userId(),
                c.version(), c.providerType(), c.enabled(), correlationId, clock.instant().toString());
        save(new Envelope(eventId, PROVIDER_CONFIG, c.id(), MessagingConfig.EVENTS_EXCHANGE, eventType,
                correlationId), payload);
    }

    /** {@code ai.provider.test} (ai-provider-test-command.schema.json). */
    @Override
    public void testRequested(ProviderConfig config, UUID correlationId) {
        UUID commandId = UUID.randomUUID();
        TestCommand payload = new TestCommand(SCHEMA_VERSION, commandId, config.id(), config.userId(),
                config.version(), correlationId, clock.instant().toString());
        save(new Envelope(commandId, PROVIDER_CONFIG, config.id(), MessagingConfig.COMMANDS_EXCHANGE, PROVIDER_TEST,
                correlationId), payload);
    }

    /** {@code ai.request.submit} (ai-request-command.schema.json). */
    @Override
    public void requestSubmitted(AiRequest request, List<ChatMessage> messages, ModelParameters parameters,
                                 String model) {
        AiRequest.Snapshot r = request.snapshot();
        RequestParameters params = parameters == null ? new RequestParameters(model, null, null, null)
                : new RequestParameters(model, parameters.temperature(), parameters.maxTokens(), parameters.topP());
        RequestCommand payload = new RequestCommand(SCHEMA_VERSION, r.id(), r.configId(), r.userId(),
                r.correlationId(), messages, params, r.createdAt().toString(), r.deadline().toString());
        save(new Envelope(r.id(), "ai_request", r.id(), MessagingConfig.COMMANDS_EXCHANGE, REQUEST_SUBMIT,
                r.correlationId()), payload);
    }

    private static String routingKey(ConfigChange change) {
        return switch (change) {
            case CREATED -> CONFIG_CREATED;
            case UPDATED -> CONFIG_UPDATED;
            case DISABLED -> CONFIG_DISABLED;
            case DELETED -> CONFIG_DELETED;
            case CREDENTIAL_ROTATED -> CREDENTIAL_ROTATED;
        };
    }

    private void save(Envelope envelope, Object payload) {
        outbox.save(OutboxEventEntity.builder()
                .id(envelope.id())
                .aggregateType(envelope.aggregateType())
                .aggregateId(envelope.aggregateId())
                .exchange(envelope.exchange())
                .eventType(envelope.routingKey())
                .correlationId(envelope.correlationId())
                .payload(json.writeValueAsString(payload))
                .createdAt(clock.instant())
                .build());
    }

    /** A dónde va un mensaje y a qué agregado pertenece. */
    private record Envelope(UUID id, String aggregateType, UUID aggregateId, String exchange, String routingKey,
                            UUID correlationId) {
    }

    record ConfigChangedEvent(int schemaVersion, UUID eventId, String eventType, UUID configId, UUID userId,
                              long version, String providerType, boolean enabled, UUID correlationId,
                              String occurredAt) {
    }

    record TestCommand(int schemaVersion, UUID commandId, UUID configId, UUID userId, long configVersion,
                       UUID correlationId, String requestedAt) {
    }

    record RequestParameters(String model, Double temperature, Integer maxTokens, Double topP) {
    }

    record RequestCommand(int schemaVersion, UUID requestId, UUID configId, UUID userId, UUID correlationId,
                          List<ChatMessage> messages, RequestParameters parameters, String submittedAt,
                          String deadline) {
    }
}
