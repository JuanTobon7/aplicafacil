package com.aplicafacil.linkedin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Transiciones de la conexión, sin Spring ni base de datos. */
class LinkedInConnectionTest {

    private final Instant t0 = Instant.parse("2026-10-19T12:00:00Z");
    private final Instant t1 = t0.plusSeconds(60);

    @Test
    void connectClearsExpirationAndErrors() {
        LinkedInConnection c = LinkedInConnection.disconnected(UUID.randomUUID(), t0);
        c.expire("SESSION_EXPIRED", t0);

        c.connect("urn:li:member:1", t1);

        assertThat(c.status()).isEqualTo(ConnectionStatus.CONNECTED);
        assertThat(c.state().memberUrn()).isEqualTo("urn:li:member:1");
        assertThat(c.state().lastError()).isNull();
        assertThat(c.timeline().expiredAt()).isNull();
        assertThat(c.timeline().connectedAt()).isEqualTo(t1);
    }

    @Test
    void incompleteSyncLeavesAVisibleCode() {
        LinkedInConnection c = LinkedInConnection.disconnected(UUID.randomUUID(), t0);
        c.connect("urn:li:member:1", t0);

        c.synced(false, t1);

        assertThat(c.state().lastError()).isEqualTo(LinkedInConnection.PROFILE_INCOMPLETE);
        assertThat(c.timeline().lastSyncAt()).isEqualTo(t1);
        assertThat(c.status()).isEqualTo(ConnectionStatus.CONNECTED);
    }

    @Test
    void expireKeepsTheReason() {
        LinkedInConnection c = LinkedInConnection.disconnected(UUID.randomUUID(), t0);

        c.expire("DECRYPT_FAILED", t1);

        assertThat(c.status()).isEqualTo(ConnectionStatus.EXPIRED);
        assertThat(c.state().lastError()).isEqualTo("DECRYPT_FAILED");
        assertThat(c.timeline().expiredAt()).isEqualTo(t1);
    }
}
