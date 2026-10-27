package com.aplicafacil.profile.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
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

import com.aplicafacil.linkedin.contract.ProfileSnapshot;
import com.aplicafacil.profile.domain.ProfileSection;
import com.aplicafacil.profile.infrastructure.persistence.JpaAiTaskRepository;
import com.aplicafacil.profile.infrastructure.persistence.JpaUserProfileRepository;
import com.aplicafacil.profile.infrastructure.persistence.entity.SkillEntity;
import com.aplicafacil.profile.infrastructure.persistence.entity.UserProfileEntity;

/** "Sincronizar de nuevo" no pisa lo editado a mano ni lo que no se pudo leer. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProfileSyncService.class, JpaUserProfileRepository.class, JpaAiTaskRepository.class,
        ProfileSyncServiceTest.Config.class})
@Testcontainers
class ProfileSyncServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"))
            .withInitScript("init-pgvector.sql");

    @Autowired
    ProfileSyncService service;

    @Autowired
    TestEntityManager em;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createsTheProfileWithItsEntitiesOnFirstSync() {
        ProfileSnapshot snapshot = new ProfileSnapshot("Resumen de LinkedIn",
                List.of(new ProfileSnapshot.Experience("Backend Developer", "ACME", YearMonth.of(2021, 3), null,
                        "Medellín", "APIs"), new ProfileSnapshot.Experience(" ", "Sin cargo", null, null, null, null)),
                List.of(), List.of("Java", "java", ""), List.of(), true, List.of());

        service.sync(userId, snapshot);

        UserProfileEntity profile = reload();
        assertThat(profile.getSummary()).isEqualTo("Resumen de LinkedIn");
        assertThat(profile.getSkills()).extracting(SkillEntity::getName).containsExactly("Java");
        assertThat(profile.getExperiences()).singleElement().satisfies(e -> {
            assertThat(e.getCompany()).isEqualTo("ACME");
            assertThat(e.getStartMonth()).isEqualTo(YearMonth.of(2021, 3));
        });
        assertThat(profile.isComplete()).isTrue();
        assertThat(profile.getLastSyncedAt()).isNotNull();
    }

    @Test
    void keepsManuallyEditedSections() {
        service.sync(userId, snapshot("Resumen de LinkedIn", List.of("Java"), true, List.of()));
        UserProfileEntity edited = em.find(UserProfileEntity.class, userId);
        edited.setSummary("Mi resumen");
        edited.setManuallyEditedFields(EnumSet.of(ProfileSection.SUMMARY));
        em.flush();

        service.sync(userId, snapshot("Resumen nuevo", List.of("Java", "Spring"), true, List.of()));

        UserProfileEntity profile = reload();
        assertThat(profile.getSummary()).isEqualTo("Mi resumen");
        assertThat(profile.getSkills()).extracting(SkillEntity::getName).containsExactly("Java", "Spring");
    }

    @Test
    void partialReadKeepsWhatCouldNotBeRead() {
        service.sync(userId, snapshot("Resumen", List.of("Java"), true, List.of()));

        service.sync(userId, snapshot(null, List.of(), false, List.of("summary", "skills")));

        UserProfileEntity profile = reload();
        assertThat(profile.getSummary()).isEqualTo("Resumen");
        assertThat(profile.getSkills()).extracting(SkillEntity::getName).containsExactly("Java");
        assertThat(profile.isComplete()).isFalse();
        assertThat(profile.getMissingSections()).containsExactly("summary", "skills");
    }

    @Test
    void deleteRemovesTheProfileAndItsEntities() {
        service.sync(userId, snapshot("Resumen", List.of("Java", "Go"), true, List.of()));
        em.flush();

        service.delete(userId);
        service.delete(userId);

        em.clear();
        assertThat(em.find(UserProfileEntity.class, userId)).isNull();
        assertThat(em.getEntityManager().createQuery("select count(s) from SkillEntity s", Long.class)
                .getSingleResult()).isZero();
    }

    private UserProfileEntity reload() {
        em.flush();
        em.clear();
        return em.find(UserProfileEntity.class, userId);
    }

    private static ProfileSnapshot snapshot(String summary, List<String> skills, boolean complete, List<String> missing) {
        return new ProfileSnapshot(summary, List.of(), List.of(), skills, List.of(), complete, missing);
    }

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
