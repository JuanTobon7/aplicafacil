package com.aplicafacil.linkedin.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInSessionEntity;

/** Solo la usa {@link JpaLinkedInSessionRepository}. */
interface SpringDataLinkedInSessionRepository extends JpaRepository<LinkedInSessionEntity, UUID> {

    Optional<LinkedInSessionEntity> findByUserId(UUID userId);

    @Modifying
    @Query("delete from LinkedInSessionEntity s where s.userId = :userId")
    int deleteByUserId(UUID userId);

    /** Toma el arrendamiento si está libre o vencido. Atómico: una sola fila, un solo UPDATE. */
    @Modifying
    @Query("""
            update LinkedInSessionEntity s
               set s.leaseOwner = :owner, s.leaseExpiresAt = :expiresAt
             where s.userId = :userId and (s.leaseOwner is null or s.leaseExpiresAt < :now)
            """)
    int acquireLease(UUID userId, UUID owner, Instant expiresAt, Instant now);

    @Modifying
    @Query("""
            update LinkedInSessionEntity s set s.leaseOwner = null, s.leaseExpiresAt = null
             where s.userId = :userId and s.leaseOwner = :owner
            """)
    int releaseLease(UUID userId, UUID owner);
}
