package com.aplicafacil.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token rotativo. Solo se guarda su SHA-256; si se reusa uno ya rotado, se revoca toda su familia.
 */
public record RefreshToken(UUID id, UUID userId, UUID familyId, Instant createdAt, Instant expiresAt,
                           Instant revokedAt) {

    public boolean revoked() {
        return revokedAt != null;
    }

    public boolean usable(Instant now) {
        return !revoked() && expiresAt.isAfter(now);
    }
}
