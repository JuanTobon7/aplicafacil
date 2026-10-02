package com.aplicafacil.people.application.port.in;

import java.util.UUID;

import com.aplicafacil.people.application.dto.PeopleDto;
import com.aplicafacil.people.application.dto.PeopleDtoRegister;
import com.aplicafacil.people.application.exception.EmailAlreadyUsedException;
import com.aplicafacil.people.application.exception.PeopleAccessDeniedException;
import com.aplicafacil.people.application.exception.PeopleAlreadyRegisteredException;
import com.aplicafacil.people.application.exception.PeopleNotFoundException;

/**
 * Puerto de entrada: CRUD de personas.
 *
 * <p>{@code userId} es siempre el usuario autenticado ({@code sub} del token).
 * Cada persona solo la lee o modifica su dueño ({@link PeopleAccessDeniedException}
 * en caso contrario).
 */
public interface PeopleService {

    /** @throws PeopleNotFoundException, PeopleAccessDeniedException */
    PeopleDto getPeople(UUID peopleId, UUID userId);

    /** @throws PeopleNotFoundException */
    PeopleDto getPeopleByUserId(UUID userId);

    /**
     * Registra la persona del usuario {@code userId}.
     *
     * @throws PeopleAlreadyRegisteredException si el usuario ya tiene persona
     * @throws EmailAlreadyUsedException        si otra persona usa el email
     */
    PeopleDto registerPeople(UUID userId, PeopleDtoRegister peopleDto);

    /**
     * Reemplaza nombre, email y datos de contacto.
     *
     * @throws PeopleNotFoundException, PeopleAccessDeniedException
     * @throws EmailAlreadyUsedException si el nuevo email ya es de otra persona
     */
    PeopleDto updatePeople(UUID peopleId, PeopleDtoRegister peopleDto, UUID userId);

    /** @throws PeopleNotFoundException, PeopleAccessDeniedException */
    void deletePeople(UUID peopleId, UUID userId);
}
