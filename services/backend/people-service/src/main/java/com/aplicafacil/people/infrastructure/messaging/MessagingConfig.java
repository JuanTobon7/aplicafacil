package com.aplicafacil.people.infrastructure.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cola de people-service para {@code user.registered}, con su dead-letter queue.
 *
 * <pre>
 *  auth-service ──[aplicafacil.events / user.registered]── people.user-registered ──▶ people-service
 * </pre>
 */
@Configuration
class MessagingConfig {

    static final String EVENTS_EXCHANGE = "aplicafacil.events";
    static final String DEAD_LETTER_EXCHANGE = "aplicafacil.dlx";
    static final String USER_REGISTERED_QUEUE = "people.user-registered";
    static final String USER_REGISTERED_ROUTING_KEY = "user.registered";

    @Bean
    Declarables peopleMessagingTopology() {
        TopicExchange events = new TopicExchange(EVENTS_EXCHANGE);
        TopicExchange dlx = new TopicExchange(DEAD_LETTER_EXCHANGE);
        Queue queue = QueueBuilder.durable(USER_REGISTERED_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(USER_REGISTERED_QUEUE)
                .build();
        Queue dlq = QueueBuilder.durable(USER_REGISTERED_QUEUE + ".dlq").build();
        return new Declarables(events, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(events).with(USER_REGISTERED_ROUTING_KEY),
                BindingBuilder.bind(dlq).to(dlx).with(USER_REGISTERED_QUEUE));
    }

    /**
     * JSON → tipo del parámetro del listener. Se ignora el header {@code __TypeId__}
     * del productor: es una clase de auth-service que aquí no existe.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        var converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
