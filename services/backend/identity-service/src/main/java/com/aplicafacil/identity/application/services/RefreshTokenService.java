package com.aplicafacil.identity.application.services;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aplicafacil.identity.application.exception.IdentityException;
import com.aplicafacil.identity.application.port.out.RefreshTokenRepository;
import com.aplicafacil.identity.domain.RefreshToken;

/**
 * Refresh tokens opacos y rotativos (cookie {@code af_rt}). Cada uso entrega uno nuevo; si se presenta
 * uno ya rotado (posible robo), se revoca toda la familia.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository tokens;
    private final TokenPolicy policy;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository tokens, TokenPolicy policy, Clock clock) {
        this.tokens = tokens;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public Issued issue(UUID userId) {
        return create(userId, UUID.randomUUID()).issued();
    }

    /** Valida el token, lo revoca y entrega el siguiente de la misma familia. */
    @Transactional(noRollbackFor = IdentityException.class)
    public Issued rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = find(rawToken);
        if (current.revoked()) {
            log.warn("Refresh token reutilizado para el usuario {}: se revoca la familia", current.userId());
            tokens.revokeFamily(current.familyId(), now);
            throw IdentityException.invalidRefreshToken();
        }
        if (!current.usable(now)) {
            throw IdentityException.invalidRefreshToken();
        }
        Created next = create(current.userId(), current.familyId());
        tokens.markReplaced(current.id(), next.token().id(), now);
        return next.issued();
    }

    /** Usuario dueño de un token vigente, sin rotarlo. */
    @Transactional(readOnly = true)
    public UUID ownerOf(String rawToken) {
        RefreshToken token = find(rawToken);
        if (!token.usable(clock.instant())) {
            throw IdentityException.invalidRefreshToken();
        }
        return token.userId();
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findByHash(SecureTokens.sha256(rawToken))
                .ifPresent(t -> tokens.revokeFamily(t.familyId(), clock.instant()));
    }

    private RefreshToken find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw IdentityException.invalidRefreshToken();
        }
        return tokens.findByHash(SecureTokens.sha256(rawToken))
                .orElseThrow(IdentityException::invalidRefreshToken);
    }

    private Created create(UUID userId, UUID familyId) {
        String raw = SecureTokens.random();
        Instant now = clock.instant();
        RefreshToken token = new RefreshToken(UUID.randomUUID(), userId, familyId, now,
                now.plus(policy.refreshTokenTtl()), null);
        tokens.save(token, SecureTokens.sha256(raw));
        return new Created(token, new Issued(userId, raw, token.expiresAt()));
    }

    private record Created(RefreshToken token, Issued issued) {
    }

    /** {@code value} solo viaja en la cookie: nunca se loguea. */
    public record Issued(UUID userId, String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "Issued[userId=" + userId + ", expiresAt=" + expiresAt + "]";
        }
    }
}
