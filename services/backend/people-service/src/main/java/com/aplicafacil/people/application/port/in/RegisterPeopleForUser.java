package com.aplicafacil.people.application.port.in;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.RegisterPeopleForUserCommand;
import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;

/**
 * Puerto de entrada: crea la persona de una cuenta recién registrada en auth-service.
 *
 * <p>Idempotente: los eventos pueden llegar repetidos (reintentos de RabbitMQ),
 * así que si la persona del usuario ya existe se devuelve tal cual.
 */
public interface RegisterPeopleForUser {

    /** @throws EmailAlreadyUsedException si otra persona (de otro usuario) ya usa el email */
    PeopleDto register(RegisterPeopleForUserCommand command);
}
