package com.aplicafacil.profile.infrastructure.messaging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aplicafacil.profile.application.ai.AiResultService;
import com.aplicafacil.profile.application.ai.AiResultService.AiResult;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Topología y consumidor de las solicitudes de IA de profile-service (contracts/events/asyncapi.yaml, ADR 0007).
 *
 * <pre>
 *  profile-service --[aplicafacil.commands]-- ai.request.submit --> mcp-gateway.commands --> mcp-gateway
 *  mcp-gateway --[aplicafacil.events]-- ai.request.completed|failed --> profile.ai-results --> AiResultService
 *  mensaje ilegible --> aplicafacil.dlx --> profile.ai-results.dlq
 * </pre>
 *
 * La misma routing key llega también a ai-config-service: cada uno aplica solo los requestId que creó.
 */
@Configuration
public class ProfileAiMessaging {

    static final String COMMANDS_EXCHANGE = "aplicafacil.commands";
    static final String EVENTS_EXCHANGE = "aplicafacil.events";
    static final String DEAD_LETTER_EXCHANGE = "aplicafacil.dlx";
    static final String SUBMIT = "ai.request.submit";
    static final String QUEUE = "profile.ai-results";
    private static final Logger log = LoggerFactory.getLogger(ProfileAiMessaging.class);

    private final AiResultService results;
    private final JsonMapper json;

    public ProfileAiMessaging(AiResultService results, JsonMapper json) {
        this.results = results;
        this.json = json;
    }

    @Bean
    Declarables profileAiTopology() {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE);
        Queue queue = QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(QUEUE)
                .build();
        Queue dlq = QueueBuilder.durable(QUEUE + ".dlq").build();
        return new Declarables(events, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(events).with("ai.request.completed"),
                BindingBuilder.bind(queue).to(events).with("ai.request.failed"),
                BindingBuilder.bind(dlq).to(dlx).with(QUEUE));
    }

    /** {@code ai.request.completed|failed} (contracts/schemas/ai-request-result-event.schema.json). */
    @RabbitListener(queues = QUEUE)
    void onAiResult(byte[] body) {
        AiResult result = parse(body);
        if (results.apply(result)) {
            log.info("Tarea de IA {} cerrada ({})", result.requestId(), result.completed() ? "completed" : "failed");
        }
    }

    private AiResult parse(byte[] body) {
        try {
            JsonNode node = json.readTree(body);
            boolean completed = "ai.request.completed".equals(node.path("eventType").asString());
            JsonNode error = node.path("error");
            return new AiResult(uuid(node, "requestId"), uuid(node, "userId"), uuid(node, "configId"), completed,
                    text(node.path("output"), "text"), completed ? null : text(error, "code"),
                    completed ? null : text(error, "message"));
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw new AmqpRejectAndDontRequeueException("Mensaje que no cumple el contrato: " + e.getMessage(), e);
        }
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.get(field).asString());
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asString() : null;
    }
}
