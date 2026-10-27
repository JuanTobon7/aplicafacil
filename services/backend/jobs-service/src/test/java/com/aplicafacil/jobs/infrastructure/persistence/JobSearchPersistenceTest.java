package com.aplicafacil.jobs.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearch;
import com.aplicafacil.jobs.domain.JobSearchStatus;
import com.aplicafacil.jobs.infrastructure.persistence.entity.JobSearchEntity;

/**
 * Flyway + entidades contra Postgres real: el upsert de ofertas y las transiciones de estado por id. El reparto por
 * RabbitMQ: {@code JobSearchQueueIntegrationTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({JdbcJobPostingStore.class, JdbcJobSearchStates.class, JobSearchPersistenceTest.ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobSearchPersistenceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18").asCompatibleSubstituteFor("postgres"));

    @Autowired
    SpringDataJobSearchRepository searches;

    @Autowired
    SpringDataJobPostingRepository postings;

    @Autowired
    JdbcJobPostingStore store;

    @Autowired
    JdbcJobSearchStates states;

    @Test
    void startAcceptsANewAttemptOrTheSameOneRedeliveredButNeverAnOldMessage() {
        UUID id = queued(UUID.randomUUID(), NOW);

        assertThat(states.start(id, 1, NOW)).get().extracting(JobSearch::attempts).isEqualTo(1);
        // La instancia murió con el intento 1 en curso y RabbitMQ lo reentregó: se retoma
        assertThat(states.start(id, 1, NOW)).isPresent();

        assertThat(states.retryLater(id, "SESSION_BUSY", "ocupada", NOW.plusSeconds(30))).isTrue();
        JobSearchEntity retried = searches.findById(id).orElseThrow();
        assertThat(retried.getStatus()).isEqualTo(JobSearchStatus.QUEUED);
        assertThat(retried.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(30));
        // Una copia vieja del intento 1 ya no corresponde; el 2 sí
        assertThat(states.start(id, 1, NOW)).isEmpty();
        assertThat(states.start(id, 2, NOW)).isPresent();

        states.complete(id, 12, 7, NOW.plusSeconds(90));
        JobSearchEntity done = searches.findById(id).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(JobSearchStatus.COMPLETED);
        assertThat(done.getAttempts()).isEqualTo(2);
        assertThat(done.getFoundCount()).isEqualTo(12);
        // Terminada: ni un mensaje repetido la reabre ni un worker atrasado pisa el estado final
        assertThat(states.start(id, 2, NOW)).isEmpty();
        states.fail(id, "LATE", "tarde", NOW.plusSeconds(100));
        assertThat(searches.findById(id).orElseThrow().getStatus()).isEqualTo(JobSearchStatus.COMPLETED);
        assertThat(states.start(UUID.randomUUID(), 1, NOW)).isEmpty();
    }

    @Test
    void postingsAreStoredOncePerPlatformAndLinkedInOrder() {
        UUID first = queued(UUID.randomUUID(), NOW);
        UUID second = queued(UUID.randomUUID(), NOW);
        String a = "a-" + UUID.randomUUID();
        String b = "b-" + UUID.randomUUID();

        assertThat(store.saveAll(first, JobPlatform.COMPUTRABAJO, List.of(card(a, "Java"), card(b, "Kotlin")))).isEqualTo(2);
        // Otra búsqueda encuentra la misma oferta (con título actualizado) y una nueva del otro portal
        assertThat(store.saveAll(second, JobPlatform.COMPUTRABAJO, List.of(card(b, "Kotlin Sr")))).isZero();
        assertThat(store.saveAll(second, JobPlatform.LINKEDIN, List.of(card(a, "Java")))).isEqualTo(1);
        // Repetir es idempotente
        assertThat(store.saveAll(first, JobPlatform.COMPUTRABAJO, List.of(card(a, "Java")))).isZero();

        assertThat(postings.findBySearch(first)).extracting(p -> p.getExternalId() + ":" + p.getIsNew())
                .containsExactly(a + ":true", b + ":true");
        assertThat(postings.findBySearch(second)).extracting(p -> p.getTitle() + "/" + p.getPlatform())
                .containsExactlyInAnyOrder("Kotlin Sr/COMPUTRABAJO", "Java/LINKEDIN");
    }

    @Test
    void aRepeatedCardInTheSameBatchIsSavedOnce() {
        UUID search = queued(UUID.randomUUID(), NOW);
        String a = "a-" + UUID.randomUUID();
        String b = "b-" + UUID.randomUUID();

        assertThat(store.saveAll(search, JobPlatform.COMPUTRABAJO,
                List.of(card(a, "Java"), card(b, "Kotlin"), card(a, "Java repetida")))).isEqualTo(2);

        assertThat(postings.findBySearch(search)).extracting(p -> p.getPosition() + ":" + p.getTitle())
                .containsExactly("0:Java", "1:Kotlin");
    }

    private UUID queued(UUID userId, Instant nextAttemptAt) {
        UUID id = UUID.randomUUID();
        searches.saveAndFlush(JobSearchEntity.builder()
                .id(id).userId(userId).platform(JobPlatform.LINKEDIN).keywords("java").maxResults(25)
                .status(JobSearchStatus.QUEUED).attempts(0).nextAttemptAt(nextAttemptAt)
                .requestedAt(NOW.minus(1, ChronoUnit.MINUTES))
                .build());
        return id;
    }

    private static JobCard card(String id, String title) {
        return new JobCard(id, "https://portal.example/jobs/" + id, title, "Acme", "Bogotá", null, null, false);
    }

    /** @TestConfiguration: no reemplaza la detección de @SpringBootApplication (sí lo haría @Configuration). */
    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
