package com.aplicafacil.identity.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.identity.application.exception.IdentityException;
import com.aplicafacil.identity.application.port.out.RefreshTokenRepository;
import com.aplicafacil.identity.domain.RefreshToken;

/** Rotación y detección de reuso, contra el puerto (sin base de datos). */
class RefreshTokenServiceTest {

    private final Instant now = Instant.parse("2026-10-19T12:00:00Z");
    private final InMemoryTokens tokens = new InMemoryTokens();
    private final RefreshTokenService service = new RefreshTokenService(tokens,
            new TokenPolicy(Duration.ofDays(30), Duration.ofSeconds(60)), Clock.fixed(now, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void rotateHandsOutANewTokenAndRevokesTheOldOne() {
        RefreshTokenService.Issued first = service.issue(userId);

        RefreshTokenService.Issued second = service.rotate(first.value());

        assertThat(second.userId()).isEqualTo(userId);
        assertThat(second.value()).isNotEqualTo(first.value());
        assertThat(service.ownerOf(second.value())).isEqualTo(userId);
        assertThatThrownBy(() -> service.ownerOf(first.value())).isInstanceOf(IdentityException.class);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        RefreshTokenService.Issued first = service.issue(userId);
        RefreshTokenService.Issued second = service.rotate(first.value());

        assertThatThrownBy(() -> service.rotate(first.value())).isInstanceOf(IdentityException.class);

        assertThatThrownBy(() -> service.ownerOf(second.value())).isInstanceOf(IdentityException.class);
    }

    @Test
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> service.rotate("no-existe")).isInstanceOf(IdentityException.class);
    }

    /** Fake del puerto: lo mínimo para observar el comportamiento del servicio. */
    private static final class InMemoryTokens implements RefreshTokenRepository {

        private final Map<ByteBuffer, RefreshToken> byHash = new HashMap<>();

        @Override
        public void save(RefreshToken token, byte[] tokenHash) {
            byHash.put(ByteBuffer.wrap(tokenHash.clone()), token);
        }

        @Override
        public Optional<RefreshToken> findByHash(byte[] tokenHash) {
            return Optional.ofNullable(byHash.get(ByteBuffer.wrap(tokenHash)));
        }

        @Override
        public void markReplaced(UUID tokenId, UUID replacedBy, Instant at) {
            byHash.replaceAll((h, t) -> t.id().equals(tokenId) ? revoked(t, at) : t);
        }

        @Override
        public void revokeFamily(UUID familyId, Instant at) {
            byHash.replaceAll((h, t) -> t.familyId().equals(familyId) && !t.revoked() ? revoked(t, at) : t);
        }

        private static RefreshToken revoked(RefreshToken t, Instant at) {
            return new RefreshToken(t.id(), t.userId(), t.familyId(), t.createdAt(), t.expiresAt(), at);
        }
    }
}
