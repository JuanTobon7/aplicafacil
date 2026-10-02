package com.aplicafacil.people.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

/** Payload de {@code user.registered}. Contrato: contracts/schemas/user-registered-event.schema.json */
record UserRegisteredMessage(UUID eventId, UUID userId, String email,
                             String firstName, String lastName, Instant occurredAt) {
}
