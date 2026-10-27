package com.aplicafacil.identity.infrastructure.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aplicafacil.identity.application.port.out.AccountEvents;
import com.aplicafacil.identity.infrastructure.persistence.SpringDataOutboxEventRepository;
import com.aplicafacil.identity.infrastructure.persistence.entity.OutboxEventEntity;

import lombok.Builder;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registra los eventos de cuenta en el outbox, con su payload ya serializado según contracts/schemas.
 * {@link OutboxPublisher} los publica después.
 */
@Component
public class OutboxEventCreator implements AccountEvents {

    public static final String USER_DELETED = "user.deleted";

    private final SpringDataOutboxEventRepository outbox;
    private final JsonMapper json;
    private final Clock clock;

    public OutboxEventCreator(SpringDataOutboxEventRepository outbox, JsonMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    /** {@code user.deleted} (contracts/schemas/user-deleted-event.schema.json). */
    @Override
    public void userDeleted(UUID userId) {
        Instant now = clock.instant();
        UUID eventId = UUID.randomUUID();
        UserDeletedEvent payload = UserDeletedEvent.builder()
                .eventId(eventId)
                .userId(userId)
                .occurredAt(now.toString())
                .build();
        outbox.save(OutboxEventEntity.builder()
                .id(eventId)
                .aggregateType("user")
                .aggregateId(userId)
                .eventType(USER_DELETED)
                .payload(json.writeValueAsString(payload))
                .createdAt(now)
                .build());
    }

    @Builder
    record UserDeletedEvent(UUID eventId, UUID userId, String occurredAt) {
    }
}
