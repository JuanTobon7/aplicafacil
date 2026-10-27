package com.aplicafacil.linkedin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.linkedin.application.port.out.LinkedInSessionRepository.SessionLease;
import com.aplicafacil.linkedin.domain.ConnectionStatus;
import com.aplicafacil.linkedin.domain.ConnectionType;
import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInConnectionEntity;
import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInSessionEntity;
import com.aplicafacil.platform.crypto.AesGcmSessionCryptoService;
import com.aplicafacil.platform.crypto.SessionCryptoService;
import com.aplicafacil.platform.crypto.SessionDecryptionException;

/** La sesión solo existe cifrada en la BD; se descifra en memoria; el arrendamiento evita ejecuciones simultáneas. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaLinkedInSessionRepository.class, JpaLinkedInSessionRepositoryTest.Config.class})
@Testcontainers
class JpaLinkedInSessionRepositoryTest {

    private static final String STATE = "{\"cookies\":[{\"name\":\"li_at\",\"value\":\"AQEDAT-secreto\"}],\"origins\":[]}";
    static final String KEY = randomKey();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    JpaLinkedInSessionRepository sessions;

    @Autowired
    SpringDataLinkedInSessionRepository rows;

    @Autowired
    TestEntityManager em;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void connection() {
        LinkedInConnectionEntity connection = new LinkedInConnectionEntity();
        connection.setUserId(userId);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setConnectionType(ConnectionType.SESSION);
        connection.setCreatedAt(Instant.now());
        connection.setUpdatedAt(Instant.now());
        em.persistAndFlush(connection);
    }

    @Test
    void storesOnlyCiphertextAndReadsItBack() {
        sessions.saveStorageState(userId, STATE);
        em.flush();
        em.clear();

        LinkedInSessionEntity row = rows.findByUserId(userId).orElseThrow();
        assertThat(new String(row.getStorageStateEncrypted())).doesNotContain("li_at", "secreto");
        assertThat(row.getIv()).hasSize(12);
        assertThat(row.getKeyVersion()).isEqualTo(1);
        assertThat(sessions.loadStorageState(userId)).contains(STATE);
    }

    @Test
    void everyWriteUsesANewIv() {
        sessions.saveStorageState(userId, STATE);
        em.flush();
        byte[] firstIv = rows.findByUserId(userId).orElseThrow().getIv().clone();

        sessions.saveStorageState(userId, STATE);
        em.flush();
        em.clear();

        assertThat(rows.findByUserId(userId).orElseThrow().getIv()).isNotEqualTo(firstIv);
        assertThat(rows.count()).isEqualTo(1);
    }

    @Test
    void aChangedKeyCannotDecryptTheSession() {
        sessions.saveStorageState(userId, STATE);
        em.flush();
        em.clear();
        JpaLinkedInSessionRepository withOtherKey = new JpaLinkedInSessionRepository(rows,
                new AesGcmSessionCryptoService(randomKey(), 1), Clock.systemUTC());

        assertThatThrownBy(() -> withOtherKey.loadStorageState(userId)).isInstanceOf(SessionDecryptionException.class);
    }

    @Test
    void onlyOneLeaseAtATimeUntilReleasedOrExpired() {
        sessions.saveStorageState(userId, STATE);

        SessionLease lease = sessions.tryAcquireLease(userId, Duration.ofMinutes(5)).orElseThrow();
        assertThat(sessions.tryAcquireLease(userId, Duration.ofMinutes(5))).isEmpty();

        sessions.releaseLease(lease);
        assertThat(sessions.tryAcquireLease(userId, Duration.ofMillis(-1))).isPresent();
        // vencido: otro proceso puede tomarlo
        assertThat(sessions.tryAcquireLease(userId, Duration.ofMinutes(5))).isPresent();
    }

    @Test
    void disconnectDeletesTheRow() {
        sessions.saveStorageState(userId, STATE);
        sessions.delete(userId);

        assertThat(sessions.exists(userId)).isFalse();
        assertThat(sessions.loadStorageState(userId)).isEmpty();
    }

    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @TestConfiguration
    static class Config {
        @Bean
        SessionCryptoService sessionCryptoService() {
            return new AesGcmSessionCryptoService(KEY, 1);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
