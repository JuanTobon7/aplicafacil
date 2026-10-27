package com.aplicafacil.computrabajo.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

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

import com.aplicafacil.computrabajo.domain.ComputrabajoConnection;
import com.aplicafacil.computrabajo.domain.ConnectionStatus;
import com.aplicafacil.computrabajo.infrastructure.persistence.entity.ComputrabajoSessionEntity;
import com.aplicafacil.platform.crypto.AesGcmSessionCryptoService;
import com.aplicafacil.platform.crypto.SessionCryptoService;

/** La sesión solo existe cifrada en la BD (con la migración real de Flyway) y se descifra en memoria. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConnectionRepository.class, JpaConnectionRepositoryTest.Config.class})
@Testcontainers
class JpaConnectionRepositoryTest {

    private static final String STATE =
            "{\"cookies\":[{\"name\":\"AspNet.SharedCookie\",\"value\":\"CfDJ8-secreto\"}],\"origins\":[]}";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    JpaConnectionRepository connections;

    @Autowired
    SpringDataComputrabajoSessionRepository rows;

    @Autowired
    TestEntityManager em;

    private final UUID userId = UUID.randomUUID();

    @Test
    void storesOnlyCiphertextAndReadsItBack() {
        connections.save(ComputrabajoConnection.connected(userId, "Ana Pérez", NOW), STATE);
        em.flush();
        em.clear();

        ComputrabajoSessionEntity row = rows.findById(userId).orElseThrow();
        assertThat(new String(row.getStorageStateEncrypted(), StandardCharsets.ISO_8859_1)).doesNotContain("secreto");
        assertThat(connections.loadStorageState(userId)).contains(STATE);
        assertThat(connections.find(userId)).contains(ComputrabajoConnection.connected(userId, "Ana Pérez", NOW));
    }

    @Test
    void updatingTheStateKeepsTheSessionAndDisconnectingDeletesIt() {
        ComputrabajoConnection connected = ComputrabajoConnection.connected(userId, "Ana", NOW);
        connections.save(connected, STATE);
        connections.update(connected.expired());
        em.flush();
        em.clear();

        assertThat(connections.find(userId).orElseThrow().status()).isEqualTo(ConnectionStatus.EXPIRED);
        assertThat(connections.loadStorageState(userId)).contains(STATE);

        connections.delete(userId);
        connections.delete(userId);
        assertThat(connections.find(userId)).isEmpty();
        assertThat(connections.loadStorageState(userId)).isEmpty();
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        SessionCryptoService sessionCryptoService() {
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            return new AesGcmSessionCryptoService(Base64.getEncoder().encodeToString(key), 1);
        }
    }
}
