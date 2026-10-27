package com.aplicafacil.jobs.infrastructure.messaging;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.jobs.infrastructure.persistence.SpringDataJobApplicationRepository;
import com.aplicafacil.jobs.infrastructure.persistence.SpringDataJobSearchRepository;

import tools.jackson.databind.json.JsonMapper;

/** {@code user.deleted}: borra las postulaciones del usuario (idempotente). */
@Configuration
public class UserDeletedListener {

    static final String QUEUE = "jobs.user-deleted";
    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);

    private final SpringDataJobApplicationRepository applications;
    private final SpringDataJobSearchRepository searches;
    private final JsonMapper json;

    public UserDeletedListener(SpringDataJobApplicationRepository applications, SpringDataJobSearchRepository searches,
                               JsonMapper json) {
        this.applications = applications;
        this.searches = searches;
        this.json = json;
    }

    @Bean
    Declarables userDeletedTopology() {
        TopicExchange events = new TopicExchange("aplicafacil.events");
        Queue queue = QueueBuilder.durable(QUEUE).build();
        return new Declarables(events, queue, BindingBuilder.bind(queue).to(events).with("user.deleted"));
    }

    @RabbitListener(queues = QUEUE)
    @Transactional
    public void onUserDeleted(byte[] body) {
        UUID userId = UUID.fromString(json.readTree(body).path("userId").asString());
        applications.deleteByUserId(userId);
        // Las ofertas encontradas son públicas y se conservan; las búsquedas (y sus resultados) son del usuario
        searches.deleteByUserId(userId);
        log.info("Postulaciones y búsquedas del usuario {} eliminadas (user.deleted)", userId);
    }
}
