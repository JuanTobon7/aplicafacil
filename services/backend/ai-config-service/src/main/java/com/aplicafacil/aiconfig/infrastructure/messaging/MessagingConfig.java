package com.aplicafacil.aiconfig.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de ai-config-service (contracts/events/asyncapi.yaml, ADR 0005).
 *
 * <pre>
 *  ai-config  --[aplicafacil.events]--   ai.provider-config.*                 --> mcp-gateway (cola exclusiva por instancia)
 *  ai-config  --[aplicafacil.commands]-- ai.provider.test, ai.request.submit  --> mcp-gateway.commands
 *  mcp-gw     --[aplicafacil.events]--   ai.provider.#                        --> ai-config.provider-status
 *  mcp-gw     --[aplicafacil.events]--   ai.request.*                         --> ai-config.ai-results
 *  identity   --[aplicafacil.events]--   user.deleted                         --> ai-config.user-deleted
 * </pre>
 *
 * Las colas de este servicio son durables y, tras 3 intentos, van a su DLQ en {@code aplicafacil.dlx}.
 * Ningún mensaje lleva API keys.
 */
@Configuration
public class MessagingConfig {

    public static final String EVENTS_EXCHANGE = "aplicafacil.events";
    public static final String COMMANDS_EXCHANGE = "aplicafacil.commands";
    public static final String DEAD_LETTER_EXCHANGE = "aplicafacil.dlx";

    public static final String PROVIDER_STATUS_QUEUE = "ai-config.provider-status";
    public static final String AI_RESULTS_QUEUE = "ai-config.ai-results";
    public static final String USER_DELETED_QUEUE = "ai-config.user-deleted";

    @Bean
    Declarables aiConfigTopology() {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        TopicExchange commands = new TopicExchange(COMMANDS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE);

        Queue status = durableWithDlq(PROVIDER_STATUS_QUEUE);
        Queue results = durableWithDlq(AI_RESULTS_QUEUE);
        Queue userDeleted = durableWithDlq(USER_DELETED_QUEUE);
        Queue statusDlq = QueueBuilder.durable(PROVIDER_STATUS_QUEUE + ".dlq").build();
        Queue resultsDlq = QueueBuilder.durable(AI_RESULTS_QUEUE + ".dlq").build();
        Queue userDeletedDlq = QueueBuilder.durable(USER_DELETED_QUEUE + ".dlq").build();

        return new Declarables(
                events, commands, dlx,
                status, results, userDeleted, statusDlq, resultsDlq, userDeletedDlq,
                bind(status, events, "ai.provider.#"),
                bind(results, events, "ai.request.*"),
                bind(userDeleted, events, "user.deleted"),
                bind(statusDlq, dlx, PROVIDER_STATUS_QUEUE),
                bind(resultsDlq, dlx, AI_RESULTS_QUEUE),
                bind(userDeletedDlq, dlx, USER_DELETED_QUEUE));
    }

    private static Queue durableWithDlq(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(name)
                .build();
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
