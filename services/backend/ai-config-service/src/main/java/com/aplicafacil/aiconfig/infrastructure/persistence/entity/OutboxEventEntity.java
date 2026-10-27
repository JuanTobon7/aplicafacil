package com.aplicafacil.aiconfig.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Evento o comando pendiente de publicar. Se inserta en la misma transacción que el cambio, así nunca se pierde
 * aunque RabbitMQ no esté disponible (mismo patrón que identity-service). El payload nunca lleva secretos.
 */
@Entity
@Table(name = "outbox_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** {@code aplicafacil.events} o {@code aplicafacil.commands}. */
    @Column(nullable = false, length = 64)
    private String exchange;

    /** Routing key, por ejemplo {@code ai.provider-config.updated}. */
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "correlation_id")
    private UUID correlationId;

    /** JSON del mensaje, ya serializado según contracts/schemas. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;
}
