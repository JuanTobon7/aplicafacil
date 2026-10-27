package com.aplicafacil.identity.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** identity-service solo publica: {@code user.deleted} en {@code aplicafacil.events} (ver contracts/schemas). */
@Configuration
public class MessagingConfig {

    public static final String EVENTS_EXCHANGE = "aplicafacil.events";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE);
    }
}
