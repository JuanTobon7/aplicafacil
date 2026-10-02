package com.aplicafacil.people.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.RegisterPeopleForUserCommand;
import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;
import com.aplicafacil.people.application.port.in.RegisterPeopleForUser;
import com.aplicafacil.people.domain.exception.DomainException;

/**
 * Adaptador de entrada: {@code user.registered} (auth-service) → crear persona.
 * Los errores de datos se rechazan sin reencolar y terminan en la dead-letter queue;
 * el resto (BD caída, etc.) se reintenta y, si sigue fallando, también termina en la DLQ.
 */
@Component
class UserRegisteredListener {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredListener.class);

    private final RegisterPeopleForUser registerPeopleForUser;

    UserRegisteredListener(RegisterPeopleForUser registerPeopleForUser) {
        this.registerPeopleForUser = registerPeopleForUser;
    }

    @RabbitListener(queues = MessagingConfig.USER_REGISTERED_QUEUE)
    void onUserRegistered(UserRegisteredMessage message) {
        try {
            PeopleDto people = registerPeopleForUser.register(new RegisterPeopleForUserCommand(
                    message.userId(), message.email(), message.firstName(), message.lastName()));
            log.info("Persona {} lista para el usuario {}", people.getId(), message.userId());
        } catch (DomainException | EmailAlreadyUsedException e) {
            throw new AmqpRejectAndDontRequeueException("user.registered inválido para " + message.userId(), e);
        }
    }
}
