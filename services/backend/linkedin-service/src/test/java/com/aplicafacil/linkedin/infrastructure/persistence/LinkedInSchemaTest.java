package com.aplicafacil.linkedin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.linkedin.domain.ConnectionStatus;
import com.aplicafacil.linkedin.domain.ConnectionType;
import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInConnectionEntity;
import com.aplicafacil.linkedin.infrastructure.persistence.entity.LinkedInSessionEntity;

/** Flyway + validación de entidades contra un Postgres real, y las restricciones de la sesión. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class LinkedInSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    TestEntityManager em;

    @Test
    void deletingTheConnectionDeletesTheSession() {
        UUID userId = UUID.randomUUID();
        em.persist(connection(userId));
        em.persist(session(userId, new byte[12]));
        em.flush();

        em.getEntityManager().createNativeQuery("DELETE FROM linkedin.linkedin_connection WHERE user_id = :id")
                .setParameter("id", userId).executeUpdate();
        em.clear();

        Long sessions = (Long) em.getEntityManager()
                .createQuery("select count(s) from LinkedInSessionEntity s where s.userId = :id")
                .setParameter("id", userId).getSingleResult();
        assertThat(sessions).isZero();
    }

    @Test
    void rejectsIvThatIsNot12Bytes() {
        UUID userId = UUID.randomUUID();
        em.persist(connection(userId));
        em.persist(session(userId, new byte[16]));

        assertThatThrownBy(() -> em.flush()).isInstanceOf(PersistenceException.class);
    }

    private static LinkedInConnectionEntity connection(UUID userId) {
        LinkedInConnectionEntity connection = new LinkedInConnectionEntity();
        connection.setUserId(userId);
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setConnectionType(ConnectionType.SESSION);
        connection.setMemberUrn("urn:li:fsd_profile:" + userId);
        Instant now = Instant.now();
        connection.setCreatedAt(now);
        connection.setUpdatedAt(now);
        return connection;
    }

    private static LinkedInSessionEntity session(UUID userId, byte[] iv) {
        LinkedInSessionEntity session = new LinkedInSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStorageStateEncrypted(new byte[] {1, 2, 3});
        session.setIv(iv);
        session.setKeyVersion(1);
        Instant now = Instant.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        return session;
    }
}
