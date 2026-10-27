package com.aplicafacil.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.aplicafacil.identity.application.port.out.RefreshTokenRepository;
import com.aplicafacil.identity.domain.RefreshToken;
import com.aplicafacil.identity.infrastructure.persistence.entity.RefreshTokenEntity;

/** Adaptador JPA de {@link RefreshTokenRepository}. */
@Repository
public class JpaRefreshTokenRepository implements RefreshTokenRepository {

    private final SpringDataRefreshTokenRepository jpa;

    public JpaRefreshTokenRepository(SpringDataRefreshTokenRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void save(RefreshToken token, byte[] tokenHash) {
        jpa.save(RefreshTokenEntity.builder()
                .id(token.id())
                .userId(token.userId())
                .familyId(token.familyId())
                .tokenHash(tokenHash.clone())
                .createdAt(token.createdAt())
                .expiresAt(token.expiresAt())
                .revokedAt(token.revokedAt())
                .build());
    }

    @Override
    public Optional<RefreshToken> findByHash(byte[] tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(e -> new RefreshToken(e.getId(), e.getUserId(), e.getFamilyId(),
                e.getCreatedAt(), e.getExpiresAt(), e.getRevokedAt()));
    }

    @Override
    public void markReplaced(UUID tokenId, UUID replacedBy, Instant now) {
        jpa.findById(tokenId).ifPresent(e -> {
            e.setRevokedAt(now);
            e.setReplacedBy(replacedBy);
        });
    }

    @Override
    public void revokeFamily(UUID familyId, Instant now) {
        jpa.revokeFamily(familyId, now);
    }
}
