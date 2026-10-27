package com.aplicafacil.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.identity.infrastructure.persistence.entity.AppUserEntity;

/** Flyway + validación de entidades contra un Postgres real, y las restricciones de identidad. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class IdentitySchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    SpringDataAppUserRepository users;

    @Autowired
    TestEntityManager em;

    @Test
    void savesUserWithEditedFieldsAsJsonb() {
        AppUserEntity user = user("urn:li:fsd_profile:ABC", null, "Ana@Example.com", true);
        user.setEditedFields(Set.of("headline"));
        users.saveAndFlush(user);
        em.clear();

        assertThat(users.findByLinkedinMemberUrn("urn:li:fsd_profile:ABC")).get()
                .satisfies(u -> assertThat(u.getEditedFields()).containsExactly("headline"));
        assertThat(users.findByVerifiedEmail("ana@example.com")).isPresent();
    }

    @Test
    void rejectsUserWithoutAnyLinkedInIdentifier() {
        assertThatThrownBy(() -> users.saveAndFlush(user(null, null, "x@example.com", false)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void verifiedEmailIsUniqueIgnoringCase() {
        users.saveAndFlush(user("urn:li:fsd_profile:A", null, "same@example.com", true));

        assertThatThrownBy(() -> users.saveAndFlush(user(null, "oidc-sub-1", "SAME@example.com", true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unverifiedEmailIsNeverAMatch() {
        users.saveAndFlush(user("urn:li:fsd_profile:B", null, "free@example.com", false));
        users.saveAndFlush(user("urn:li:fsd_profile:C", null, "free@example.com", false));

        assertThat(users.findByVerifiedEmail("free@example.com")).isEmpty();
    }

    private static AppUserEntity user(String urn, String sub, String email, boolean verified) {
        AppUserEntity user = new AppUserEntity();
        user.setId(UUID.randomUUID());
        user.setLinkedinMemberUrn(urn);
        user.setLinkedinOidcSub(sub);
        user.setEmail(email);
        user.setEmailVerified(verified);
        user.setFirstName("Ana");
        Instant now = Instant.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}
