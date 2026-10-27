package com.aplicafacil.aiconfig.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.aplicafacil.aiconfig.domain.AiRequestStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Solicitud de IA asíncrona. El prompt no se guarda: viaja solo en el comando a RabbitMQ.
 * {@code id} es el requestId con el que se correlaciona el resultado.
 */
@Entity
@Table(name = "ai_request")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRequestEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "config_id", nullable = false)
    private UUID configId;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiRequestStatus status;

    @Column(name = "output_text")
    private String outputText;

    @Column(name = "output_model", length = 200)
    private String outputModel;

    @Column(name = "finish_reason", length = 64)
    private String finishReason;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "completed_at")
    private Instant completedAt;
}
