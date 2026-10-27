package com.aplicafacil.profile.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

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

import com.aplicafacil.profile.domain.ProfileSection;
import com.aplicafacil.profile.domain.SkillCategory;
import com.aplicafacil.profile.domain.SkillLevel;
import com.aplicafacil.profile.infrastructure.persistence.entity.ExperienceEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.LanguageEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.SkillEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.UserProfileEntity;

/** V1→V4 con Flyway y round-trip de las entidades hijas (orden incluido) contra un Postgres real. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserProfileSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"))
            .withInitScript("init-pgvector.sql");

    @Autowired
    SpringDataUserProfileRepository profiles;

    @Autowired
    TestEntityManager em;

    @Test
    void entitiesSurviveARoundTripInOrder() {
        UUID userId = UUID.randomUUID();
        UserProfileEntity profile = new UserProfileEntity();
        profile.setUserId(userId);
        profile.setHeadline("Backend developer");
        profile.setExperiences(new ArrayList<>(List.of(new ExperienceEntity(UUID.randomUUID(), "Backend Developer",
                "ACME", "Medellín", YearMonth.of(2021, 3), null, "APIs con Spring", List.of("Spring Boot")))));
        profile.setSkills(new ArrayList<>(List.of(
                new SkillEntity(UUID.randomUUID(), "Spring", SkillCategory.FRAMEWORK, null, null),
                new SkillEntity(UUID.randomUUID(), "Java", SkillCategory.LANGUAGE, SkillLevel.EXPERT, 6))));
        profile.setLanguages(new ArrayList<>(List.of(new LanguageEntity(UUID.randomUUID(), "Inglés", "Profesional"))));
        profile.setManuallyEditedFields(EnumSet.of(ProfileSection.SUMMARY));
        profile.setMissingSections(List.of("education"));
        profile.getSearch().setKeywords("desarrollador backend java");
        profile.getSearch().setRemoteOnly(true);
        profile.getSearch().setPostedWithinDays(7);
        Instant now = Instant.now();
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);

        profiles.saveAndFlush(profile);
        em.clear();
        UserProfileEntity loaded = profiles.findById(userId).orElseThrow();

        assertThat(loaded.getExperiences()).singleElement().satisfies(e -> {
            assertThat(e.getCompany()).isEqualTo("ACME");
            assertThat(e.getStartMonth()).isEqualTo(YearMonth.of(2021, 3));
            assertThat(e.getEndMonth()).isNull();
            assertThat(e.getTechnologies()).containsExactly("Spring Boot");
        });
        assertThat(loaded.getSkills()).extracting(SkillEntity::getName).containsExactly("Spring", "Java");
        assertThat(loaded.getSkills().get(1).getLevel()).isEqualTo(SkillLevel.EXPERT);
        assertThat(loaded.getLanguages()).extracting(LanguageEntity::getName).containsExactly("Inglés");
        assertThat(loaded.getManuallyEditedFields()).containsExactly(ProfileSection.SUMMARY);
        assertThat(loaded.getMissingSections()).containsExactly("education");
        assertThat(loaded.getSearch().getKeywords()).isEqualTo("desarrollador backend java");
        assertThat(loaded.getSearch().isRemoteOnly()).isTrue();
        assertThat(loaded.getSearch().getPostedWithinDays()).isEqualTo(7);
    }
}
