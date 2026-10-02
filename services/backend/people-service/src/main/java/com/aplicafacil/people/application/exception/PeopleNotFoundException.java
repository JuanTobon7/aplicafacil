package com.aplicafacil.people.application.exception;

import java.util.UUID;

/** No existe la persona buscada. */
public class PeopleNotFoundException extends RuntimeException {

    private PeopleNotFoundException(String message) {
        super(message);
    }

    public static PeopleNotFoundException byId(UUID id) {
        return new PeopleNotFoundException("No existe la persona " + id);
    }

    public static PeopleNotFoundException byUserId(UUID userId) {
        return new PeopleNotFoundException("El usuario " + userId + " no tiene persona registrada");
    }
}
