package com.aplicafacil.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Código de un solo uso que el front canjea por tokens al terminar la conexión con LinkedIn. Solo se guarda su hash.
 *
 * @param codeHash SHA-256 en hexadecimal
 */
public record LoginCode(String codeHash, UUID userId, UUID attemptId, Instant expiresAt, Instant consumedAt) {

    /** Sirve solo para su intento, una vez y antes de vencer. */
    public boolean redeemableFor(UUID attempt, Instant now) {
        return attemptId.equals(attempt) && consumedAt == null && expiresAt.isAfter(now);
    }
}
