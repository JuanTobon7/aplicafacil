package com.aplicafacil.auth.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio: se registró una cuenta nueva. people-service lo consume
 * para crear la {@code Person} asociada ({@code Person.userId = userId}).
 * Contrato: contracts/schemas/user-registered-event.schema.json
 */
public record UserRegistered(UUID eventId, UUID userId, String email,
                             String firstName, String lastName, Instant occurredAt) {
}
