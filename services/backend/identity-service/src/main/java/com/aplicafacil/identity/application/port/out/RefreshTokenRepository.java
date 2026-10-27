package com.aplicafacil.identity.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.identity.domain.RefreshToken;

/** Refresh tokens ({@code refresh_token}); se buscan por el SHA-256 del valor. */
public interface RefreshTokenRepository {

    void save(RefreshToken token, byte[] tokenHash);

    Optional<RefreshToken> findByHash(byte[] tokenHash);

    /** Revoca el token y apunta al que lo reemplaza. */
    void markReplaced(UUID tokenId, UUID replacedBy, Instant now);

    void revokeFamily(UUID familyId, Instant now);
}
