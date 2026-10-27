package com.aplicafacil.aiconfig.infrastructure.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.aplicafacil.aiconfig.application.AiRequestService;
import com.aplicafacil.aiconfig.application.AiRequestService.ResultEvent;
import com.aplicafacil.aiconfig.application.ProviderConfigLifecycle;
import com.aplicafacil.aiconfig.application.ProviderStatusService;
import com.aplicafacil.aiconfig.application.ProviderStatusService.StatusEvent;
import com.aplicafacil.aiconfig.domain.ProviderConfigStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidores de ai-config-service. Todos son idempotentes (ver los servicios). Un mensaje que no cumple el
 * contrato se rechaza sin reencolar y termina en la DLQ de su cola para inspeccionarlo.
 */
@Component
public class AiEventListeners {

    private static final Logger log = LoggerFactory.getLogger(AiEventListeners.class);

    private final ProviderStatusService statuses;
    private final AiRequestService requests;
    private final ProviderConfigLifecycle configs;
    private final JsonMapper json;

    public AiEventListeners(ProviderStatusService statuses, AiRequestService requests, ProviderConfigLifecycle configs,
                            JsonMapper json) {
        this.statuses = statuses;
        this.requests = requests;
        this.configs = configs;
        this.json = json;
    }

    /** {@code ai.provider.*} del mcp-gateway. */
    @RabbitListener(queues = MessagingConfig.PROVIDER_STATUS_QUEUE)
    void onProviderStatus(byte[] body) {
        StatusEvent event = parse(body, node -> new StatusEvent(
                text(node, "eventType"),
                uuid(node, "configId"),
                uuid(node, "userId"),
                required(node, "configVersion").asLong(),
                ProviderConfigStatus.valueOf(text(node, "status")),
                optionalText(node, "errorCode"),
                optionalText(node, "errorMessage"),
                strings(node.get("models")),
                node.hasNonNull("latencyMs") ? node.get("latencyMs").asLong() : null,
                uuid(node, "correlationId")));
        boolean changed = statuses.apply(event);
        log.debug("{} de {} (v{}): {}", event.eventType(), event.configId(), event.configVersion(),
                changed ? "aplicado" : "ignorado");
    }

    /** {@code ai.request.completed|failed} del mcp-gateway. */
    @RabbitListener(queues = MessagingConfig.AI_RESULTS_QUEUE)
    void onAiResult(byte[] body) {
        ResultEvent event = parse(body, node -> {
            boolean completed = "ai.request.completed".equals(text(node, "eventType"));
            JsonNode output = node.path("output");
            JsonNode usage = output.path("usage");
            JsonNode error = node.path("error");
            return new ResultEvent(
                    uuid(node, "requestId"),
                    uuid(node, "configId"),
                    uuid(node, "userId"),
                    completed,
                    optionalText(output, "text"),
                    optionalText(output, "model"),
                    optionalText(output, "finishReason"),
                    usage.hasNonNull("inputTokens") ? usage.get("inputTokens").asInt() : null,
                    usage.hasNonNull("outputTokens") ? usage.get("outputTokens").asInt() : null,
                    completed ? null : optionalText(error, "code"),
                    completed ? null : optionalText(error, "message"),
                    node.hasNonNull("latencyMs") ? node.get("latencyMs").asLong() : null);
        });
        if (!requests.applyResult(event)) {
            log.debug("Resultado de IA {} ignorado (repetido, tardío o ajeno)", event.requestId());
        }
    }

    /** {@code user.deleted} de identity-service. */
    @RabbitListener(queues = MessagingConfig.USER_DELETED_QUEUE)
    void onUserDeleted(byte[] body) {
        UUID userId = parse(body, node -> uuid(node, "userId"));
        configs.deleteAll(userId);
        log.info("Configuraciones de IA del usuario {} eliminadas (user.deleted)", userId);
    }

    private <T> T parse(byte[] body, java.util.function.Function<JsonNode, T> reader) {
        try {
            return reader.apply(json.readTree(body));
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw new AmqpRejectAndDontRequeueException("Mensaje que no cumple el contrato: " + e.getMessage(), e);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("falta " + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        return required(node, field).asString();
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(text(node, field));
    }

    private static String optionalText(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asString() : null;
    }

    private static List<String> strings(JsonNode array) {
        if (array == null || !array.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asString()));
        return values;
    }
}
