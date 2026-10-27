package com.aplicafacil.computrabajo.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ComputrabajoConnectionTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @Test
    void aNewConnectionIsValidatedNowAndNormalizesTheName() {
        ComputrabajoConnection connection = ComputrabajoConnection.connected(UUID.randomUUID(), "  Ana Pérez ", NOW);

        assertThat(connection.status()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(connection.candidateName()).isEqualTo("Ana Pérez");
        assertThat(connection.validatedAfter(NOW.minusSeconds(1))).isTrue();
        assertThat(ComputrabajoConnection.connected(UUID.randomUUID(), " ", NOW).candidateName()).isNull();
    }

    @Test
    void anExpiredConnectionIsNeverFreshAndRevalidatingReconnectsIt() {
        ComputrabajoConnection expired = ComputrabajoConnection.connected(UUID.randomUUID(), "Ana", NOW).expired();

        assertThat(expired.status()).isEqualTo(ConnectionStatus.EXPIRED);
        assertThat(expired.validatedAfter(NOW.minus(Duration.ofDays(1)))).isFalse();

        ComputrabajoConnection later = expired.validated(NOW.plusSeconds(60));
        assertThat(later.status()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(later.connectedAt()).isEqualTo(NOW);
    }
}
