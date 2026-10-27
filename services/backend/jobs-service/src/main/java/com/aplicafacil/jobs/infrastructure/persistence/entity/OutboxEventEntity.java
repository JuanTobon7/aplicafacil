package com.aplicafacil.jobs.infrastructure.persistence.entity;

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

/** Un mensaje para RabbitMQ guardado en la misma transacción que el cambio que lo origina (outbox). */
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

    /** Routing key, por ejemplo {@code jobs.search.requested}. */
    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

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
