package com.aplicafacil.identity.infrastructure.messaging;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.identity.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.aplicafacil.identity.infrastructure.persistence.entity.OutboxEventEntity;

import java.nio.charset.StandardCharsets;

/**
 * Publica los eventos pendientes del outbox en {@code aplicafacil.events}. Si RabbitMQ no está disponible,
 * los eventos se quedan en la tabla y se reintentan en la siguiente pasada (entrega al menos una vez;
 * los consumidores son idempotentes).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final SpringDataOutboxEventRepository outbox;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    public OutboxPublisher(SpringDataOutboxEventRepository outbox, RabbitTemplate rabbit, Clock clock) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 2_000, initialDelay = 5_000)
    @Transactional
    public void publishPending() {
        for (OutboxEventEntity event : outbox.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setMessageId(event.getId().toString())
                        .build();
                rabbit.send(MessagingConfig.EVENTS_EXCHANGE, event.getEventType(), message);
                event.setPublishedAt(clock.instant());
            } catch (RuntimeException e) {
                event.setAttempts(event.getAttempts() + 1);
                log.warn("No se pudo publicar {} (intento {}): {}", event.getEventType(), event.getAttempts(),
                        e.getMessage());
                return;
            }
        }
    }
}
