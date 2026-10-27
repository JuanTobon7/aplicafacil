package com.aplicafacil.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.identity.infrastructure.persistence.entity.RefreshTokenEntity;

public interface SpringDataRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(byte[] tokenHash);

    @Modifying
    @Query("update RefreshTokenEntity t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now);
}
