package com.aplicafacil.profile.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.aplicafacil.profile.domain.AiTaskKind;
import com.aplicafacil.profile.domain.AiTaskStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una solicitud de IA de profile-service; {@code id} = requestId de {@code ai.request.submit}. */
@Entity
@Table(name = "ai_task")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "config_id", nullable = false, updatable = false)
    private UUID configId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AiTaskKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AiTaskStatus status;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "completed_at")
    private Instant completedAt;
}
