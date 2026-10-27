package com.aplicafacil.profile.infrastructure.messaging;

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

import com.aplicafacil.profile.application.ProfileSyncService;

import tools.jackson.databind.json.JsonMapper;

/** {@code user.deleted} (contracts/schemas/user-deleted-event.schema.json): borra el perfil del usuario. */
@Configuration
public class UserDeletedListener {

    static final String QUEUE = "profile.user-deleted";
    private static final Logger log = LoggerFactory.getLogger(UserDeletedListener.class);

    private final ProfileSyncService profiles;
    private final JsonMapper json;

    public UserDeletedListener(ProfileSyncService profiles, JsonMapper json) {
        this.profiles = profiles;
        this.json = json;
    }

    @Bean
    Declarables userDeletedTopology() {
        TopicExchange events = new TopicExchange("aplicafacil.events");
        Queue queue = QueueBuilder.durable(QUEUE).build();
        return new Declarables(events, queue, BindingBuilder.bind(queue).to(events).with("user.deleted"));
    }

    @RabbitListener(queues = QUEUE)
    void onUserDeleted(byte[] body) {
        UUID userId = UUID.fromString(json.readTree(body).path("userId").asString());
        profiles.delete(userId);
        log.info("Perfil del usuario {} eliminado (user.deleted)", userId);
    }
}
