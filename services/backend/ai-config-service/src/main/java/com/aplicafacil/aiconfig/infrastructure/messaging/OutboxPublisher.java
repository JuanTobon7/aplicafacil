package com.aplicafacil.aiconfig.infrastructure.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.aiconfig.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.aplicafacil.aiconfig.infrastructure.persistence.entity.OutboxEventEntity;

/**
 * Publica el outbox en RabbitMQ (al menos una vez; los consumidores son idempotentes). Si RabbitMQ no está, los
 * mensajes esperan en la tabla y se reintentan en la siguiente pasada, en orden.
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

    @Scheduled(fixedDelay = 500, initialDelay = 3_000)
    @Transactional
    public void publishPending() {
        for (OutboxEventEntity event : outbox.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setMessageId(event.getId().toString())
                        .setType(event.getEventType())
                        .setTimestamp(Date.from(event.getCreatedAt()))
                        .setCorrelationId(event.getCorrelationId() == null ? null : event.getCorrelationId().toString())
                        .build();
                rabbit.send(event.getExchange(), event.getEventType(), message);
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
