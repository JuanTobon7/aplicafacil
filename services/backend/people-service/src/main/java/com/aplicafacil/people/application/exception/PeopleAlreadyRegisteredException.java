package com.aplicafacil.people.application.exception;

import java.util.UUID;

/** El usuario ya tiene su persona: una persona por usuario. */
public class PeopleAlreadyRegisteredException extends RuntimeException {

    public PeopleAlreadyRegisteredException(UUID userId) {
        super("El usuario " + userId + " ya tiene una persona registrada");
    }
}
