package com.aplicafacil.linkedin.infrastructure.messaging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.aplicafacil.linkedin.application.services.ConnectionStateService;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@code user.deleted}: identity-service ya borró la sesión de forma síncrona; esto es la red de seguridad
 * (idempotente) por si ese llamado se perdió.
 */
@Configuration
public class UserDeletedListener {

    static final String QUEUE = "linkedin.user-deleted";
    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);

    private final ConnectionStateService connections;
    private final JsonMapper json;

    public UserDeletedListener(ConnectionStateService connections, JsonMapper json) {
        this.connections = connections;
        this.json = json;
    }

    @Bean
    Declarables userDeletedTopology() {
        TopicExchange events = new TopicExchange("aplicafacil.events");
        Queue queue = QueueBuilder.durable(QUEUE).build();
        Binding binding = BindingBuilder.bind(queue).to(events).with("user.deleted");
        return new Declarables(events, queue, binding);
    }

    @RabbitListener(queues = QUEUE)
    void onUserDeleted(byte[] body) {
        UUID userId = UUID.fromString(json.readTree(body).path("userId").asString());
        connections.deleteAll(userId);
        log.info("Datos de LinkedIn del usuario {} eliminados (user.deleted)", userId);
    }
}
