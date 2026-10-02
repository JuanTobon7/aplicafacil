package com.aplicafacil.auth.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.aplicafacil.auth.application.port.UserEventPublisher;
import com.aplicafacil.auth.domain.UserRegistered;

/**
 * Publica {@link UserRegistered} en el exchange de eventos, <b>después</b> del
 * commit: si el registro falla, no sale ningún evento.
 *
 * <p>Limitación conocida: si RabbitMQ no está disponible justo después del
 * commit, el evento se pierde (se registra en el log). La solución completa es
 * un transactional outbox.
 */
@Component
class RabbitUserEventPublisher implements UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitUserEventPublisher.class);

    private final RabbitTemplate rabbit;

    RabbitUserEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publish(UserRegistered event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(event);
                }
            });
        } else {
            send(event);
        }
    }

    private void send(UserRegistered event) {
        try {
            rabbit.convertAndSend(MessagingConfig.EVENTS_EXCHANGE, MessagingConfig.USER_REGISTERED_ROUTING_KEY, event);
        } catch (RuntimeException e) {
            log.error("No se pudo publicar user.registered para el usuario {}: {}", event.userId(), e.getMessage());
        }
    }
}
