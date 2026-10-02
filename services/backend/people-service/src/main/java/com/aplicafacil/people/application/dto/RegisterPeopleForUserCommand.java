package com.aplicafacil.people.application.dto;

import java.util.UUID;

/** Datos mínimos de una cuenta recién creada en auth-service. */
public record RegisterPeopleForUserCommand(UUID userId, String email, String firstName, String lastName) {
}
