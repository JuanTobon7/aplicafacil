package com.aplicafacil.people.application.exception;

import java.util.UUID;

/** La persona no pertenece al usuario que la pide. */
public class PeopleAccessDeniedException extends RuntimeException {

    public PeopleAccessDeniedException(UUID peopleId) {
        super("La persona " + peopleId + " no pertenece al usuario autenticado");
    }
}
