package com.aplicafacil.computrabajo.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.aplicafacil.computrabajo.domain.ConnectionStatus;

/**
 * Conexión + storageState cifrado. Solo {@code JpaConnectionRepository} lee o escribe esta tabla.
 *
 * <p>Sin {@code @ToString} ni {@code @Data}: el contenido cifrado tampoco debe aparecer en los logs.
 */
@Entity
@Table(name = "computrabajo_session")
@Getter
@Setter
@NoArgsConstructor
public class ComputrabajoSessionEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConnectionStatus status;

    @Column(name = "candidate_name", length = 200)
    private String candidateName;

    @Column(name = "storage_state_encrypted", nullable = false)
    private byte[] storageStateEncrypted;

    @Column(nullable = false)
    private byte[] iv;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
