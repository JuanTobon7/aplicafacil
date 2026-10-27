package com.aplicafacil.linkedin.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * storageState de Playwright cifrado. Solo {@code LinkedInSessionRepository} lee o escribe esta tabla.
 *
 * <p>Sin {@code @ToString} ni {@code @Data}: el contenido cifrado tampoco debe aparecer en los logs.
 */
@Entity
@Table(name = "linkedin_session")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkedInSessionEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "storage_state_encrypted", nullable = false)
    private byte[] storageStateEncrypted;

    @Column(nullable = false)
    private byte[] iv;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_validated_at")
    private Instant lastValidatedAt;

    @Column(name = "lease_owner")
    private UUID leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Version
    private long version;
}
