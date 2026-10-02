package com.aplicafacil.people.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.people.domain.model.People;
import com.aplicafacil.people.persistence.mapper.PeoplePersistenceMapperImpl;

/** Flyway + entidad + mapper + adaptador contra un Postgres real. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PeopleRepositoryAdapter.class, PeoplePersistenceMapperImpl.class})
@Testcontainers
class PeopleRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    PeopleRepositoryAdapter repository;

    @Autowired
    TestEntityManager em;

    @Test
    void savesAndReloadsAPeople() {
        People people = People.create(UUID.randomUUID(), "Juan", "Tobón", "juan@example.com");
        people.updateContactDetails("+57 300 000 0000", "https://linkedin.com/in/juan", null, null, null);

        People saved = repository.save(people);
        em.clear();
        People loaded = repository.findById(people.getId()).orElseThrow();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(loaded.getId()).isEqualTo(people.getId());
        assertThat(loaded.getFullName()).isEqualTo("Juan Tobón");
        assertThat(loaded.getEmail()).isEqualTo("juan@example.com");
        assertThat(loaded.getPhone()).isEqualTo("+57 300 000 0000");
        assertThat(loaded.getLinkedinUrl()).isEqualTo("https://linkedin.com/in/juan");
        assertThat(loaded.getGithubUrl()).isNull();
    }

    @Test
    void findsThePeopleOfAUser() {
        UUID userId = UUID.randomUUID();
        People people = repository.save(People.create(userId, "Ana", "Gómez", "ana@example.com"));
        em.clear();

        assertThat(repository.findByUserId(userId)).map(People::getId).contains(people.getId());
        assertThat(repository.existsByUserId(userId)).isTrue();
        assertThat(repository.existsByUserId(UUID.randomUUID())).isFalse();
    }

    @Test
    void aUserHasASinglePeople() {
        UUID userId = UUID.randomUUID();
        repository.save(People.create(userId, "Ana", "Gómez", "ana@example.com"));

        assertThatThrownBy(() -> repository.save(People.create(userId, "Ana", "Otra", "otra@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByEmailIgnoringCase() {
        repository.save(People.create(UUID.randomUUID(), "Ana", "Gómez", "ana@example.com"));
        em.clear();

        assertThat(repository.findByEmail("  ANA@Example.com ")).isPresent();
        assertThat(repository.existsByEmail("ana@EXAMPLE.com")).isTrue();
        assertThat(repository.existsByEmail("otra@example.com")).isFalse();
    }

    @Test
    void emailIsUnique() {
        repository.save(People.create(UUID.randomUUID(), "Ana", "Gómez", "ana@example.com"));

        assertThatThrownBy(() -> repository.save(People.create(UUID.randomUUID(), "Otra", "Ana", "ANA@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateKeepsCreatedAt() {
        People created = repository.save(People.create(UUID.randomUUID(), "Juan", "Tobón", "juan@example.com"));
        em.clear();

        People people = repository.findById(created.getId()).orElseThrow();
        people.rename("Juan Carlos", "Tobón");
        People updated = repository.save(people);
        em.clear();

        assertThat(updated.getFirstName()).isEqualTo("Juan Carlos");
        assertThat(updated.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(repository.findById(created.getId()).orElseThrow().getFirstName()).isEqualTo("Juan Carlos");
    }

    @Test
    void deletesById() {
        People people = repository.save(People.create(UUID.randomUUID(), "Juan", "Tobón", "juan@example.com"));

        repository.deleteById(people.getId());
        em.flush();
        em.clear();

        assertThat(repository.findById(people.getId())).isEmpty();
    }
}
