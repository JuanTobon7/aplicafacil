package com.aplicafacil.auth.infrastructure.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth-service solo publica: declara el exchange de eventos (idempotente,
 * mismo que declaran los demás servicios). Cada consumidor declara su cola.
 */
@Configuration
class MessagingConfig {

    static final String EVENTS_EXCHANGE = "aplicafacil.events";
    static final String USER_REGISTERED_ROUTING_KEY = "user.registered";

    @Bean
    TopicExchange eventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
