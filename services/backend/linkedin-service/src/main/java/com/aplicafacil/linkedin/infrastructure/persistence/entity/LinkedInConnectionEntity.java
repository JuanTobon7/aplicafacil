package com.aplicafacil.linkedin.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.aplicafacil.linkedin.domain.ConnectionStatus;
import com.aplicafacil.linkedin.domain.ConnectionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Estado persistente de la conexión de un usuario con LinkedIn. */
@Entity
@Table(name = "linkedin_connection")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInConnectionEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConnectionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 16)
    private ConnectionType connectionType;

    /** Para verificar en una reconexión que inicia sesión el mismo miembro. */
    @Column(name = "member_urn", unique = true)
    private String memberUrn;

    /** Código del error, nunca datos de la sesión. */
    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
