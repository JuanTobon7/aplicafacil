package com.aplicafacil.profile.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.profile.domain.Cv;
import com.aplicafacil.profile.domain.Education;
import com.aplicafacil.profile.domain.Experience;
import com.aplicafacil.profile.domain.Profile;
import com.aplicafacil.profile.domain.Project;
import com.aplicafacil.profile.domain.Skill;
import com.aplicafacil.profile.infrastructure.persistence.mapper.ProfilePersistenceMapperImpl;

/** Flyway + entidades + mapper + adaptador del agregado Profile contra un Postgres real. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProfileRepositoryAdapter.class, ProfilePersistenceMapperImpl.class})
@Testcontainers
class ProfileRepositoryAdapterTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"))
            .withInitScript("init-pgvector.sql");

    @Autowired
    ProfileRepositoryAdapter repository;

    @Autowired
    TestEntityManager em;

    private final UUID personId = UUID.randomUUID();

    @Test
    void savesAndReloadsTheWholeAggregate() {
        Profile profile = fullProfile();

        repository.save(profile);
        em.clear();
        Profile loaded = repository.findById(profile.getId()).orElseThrow();

        assertThat(loaded.getPersonId()).isEqualTo(personId);
        assertThat(loaded.getTitle()).isEqualTo("Backend Developer");
        assertThat(loaded.getSkills()).extracting(Skill::getName).containsExactly("Java", "Spring");
        assertThat(loaded.getSkills()).extracting(Skill::getYearsOfExperience).containsExactly(5, 3);
        // @OrderBy startDate DESC: la experiencia más reciente primero
        assertThat(loaded.getExperiences()).extracting(Experience::getCompanyName).containsExactly("Globant", "ACME");
        assertThat(loaded.getExperiences().getFirst().isCurrent()).isTrue();
        assertThat(loaded.getEducations()).singleElement()
                .satisfies(e -> assertThat(e.getEndDate()).isEqualTo(LocalDate.of(2020, 12, 15)));
        assertThat(loaded.getProjects()).singleElement()
                .satisfies(p -> assertThat(p.getTechnologies()).isEqualTo("Java, Spring, Postgres"));
        assertThat(loaded.getCv().getLocation()).isEqualTo("cvs/juan.pdf");
        assertThat(loaded.getCv().getMimeType()).isEqualTo("application/pdf");
    }

    @Test
    void removedChildrenAndReplacedCvAreDeleted() {
        Profile profile = fullProfile();
        repository.save(profile);
        em.clear();

        Profile loaded = repository.findById(profile.getId()).orElseThrow();
        loaded.removeSkill(loaded.getSkills().getFirst().getId());
        loaded.removeProject(loaded.getProjects().getFirst().getId());
        loaded.attachCv(Cv.create("cvs/juan-v2.pdf", "application/pdf"));
        repository.save(loaded);
        em.clear();

        assertThat(count("skills")).isEqualTo(1);
        assertThat(count("projects")).isZero();
        assertThat(count("cvs")).isEqualTo(1);
        assertThat(repository.findById(profile.getId()).orElseThrow().getCv().getLocation())
                .isEqualTo("cvs/juan-v2.pdf");
    }

    @Test
    void deletingAProfileDeletesEverythingItOwns() {
        Profile profile = fullProfile();
        repository.save(profile);
        em.clear();

        repository.deleteById(profile.getId());
        em.flush();
        em.clear();

        assertThat(repository.findById(profile.getId())).isEmpty();
        for (String table : new String[] {"skills", "experiences", "education", "projects", "cvs"}) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void findsTheProfilesOfAPerson() {
        repository.save(Profile.create(personId, "Backend", "Resumen"));
        repository.save(Profile.create(personId, "Data", "Resumen"));
        repository.save(Profile.create(UUID.randomUUID(), "Otra persona", "Resumen"));
        em.clear();

        assertThat(repository.findByPersonId(personId))
                .extracting(Profile::getTitle).containsExactlyInAnyOrder("Backend", "Data");
    }

    private Profile fullProfile() {
        Profile profile = Profile.create(personId, "Backend Developer", "Desarrollador backend con foco en Java");
        profile.addSkill(Skill.create("Spring", "Boot, Data, Security", 3));
        profile.addSkill(Skill.create("Java", null, 5));
        profile.addExperience(Experience.create("ACME", "Junior Dev", null,
                LocalDate.of(2019, 1, 10), LocalDate.of(2021, 6, 30)));
        profile.addExperience(Experience.create("Globant", "Backend Dev", "APIs REST",
                LocalDate.of(2021, 7, 1), null));
        profile.addEducation(Education.create("Universidad de Antioquia", "Ingeniería de Sistemas",
                LocalDate.of(2015, 2, 1), LocalDate.of(2020, 12, 15)));
        profile.addProject(Project.create("AplicaFacil", "Automatización de postulaciones", "Java, Spring, Postgres"));
        profile.attachCv(Cv.create("cvs/juan.pdf", "application/pdf"));
        return profile;
    }

    private long count(String table) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("select count(*) from profile." + table)
                .getSingleResult()).longValue();
    }
}
