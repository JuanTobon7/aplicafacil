package com.aplicafacil.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.jobs.application.JobSearchFacadeTest.RecordingStore;
import com.aplicafacil.jobs.application.JobSearchFacadeTest.StubGateway;
import com.aplicafacil.jobs.application.port.out.JobPortalException;
import com.aplicafacil.jobs.application.port.out.JobSearchCommands;
import com.aplicafacil.jobs.application.port.out.JobSearchStates;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearch;
import com.aplicafacil.jobs.domain.SearchCriteria;

/** Política del worker: completar, reintentar con espera o fallar, e ignorar mensajes que ya no corresponden. */
class JobSearchWorkerTest {

    private static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    private static final RetryPolicy RETRIES = new RetryPolicy(3, Duration.ofSeconds(30), Duration.ofMinutes(10));

    private final FakeStates states = new FakeStates();
    private final FakeCommands commands = new FakeCommands();
    private final RecordingStore store = new RecordingStore();

    @Test
    void runsTheAttemptAndCompletesTheSearch() {
        JobSearch search = states.queued();

        worker(new StubGateway(JobPlatform.LINKEDIN, List.of(JobSearchFacadeTest.card("1")))).handle(search.id(), 1);

        assertThat(states.completed).containsExactly("found=1 created=1");
        assertThat(commands.retries).isEmpty();
    }

    @Test
    void aMessageThatNoLongerAppliesIsIgnored() {
        worker(new StubGateway(JobPlatform.LINKEDIN, List.of())).handle(UUID.randomUUID(), 1);

        assertThat(states.completed).isEmpty();
        assertThat(states.failed).isEmpty();
    }

    @Test
    void transientFailuresScheduleTheNextAttemptAfterItsBackoff() {
        JobSearch search = states.queued();
        JobSearchWorker failing = workerThrowing(new JobPortalException("SESSION_BUSY", "ocupada", true));

        failing.handle(search.id(), 1);
        failing.handle(search.id(), 2);

        assertThat(commands.retries).containsExactly(search.id() + "#2", search.id() + "#3");
        assertThat(states.nextAttempts).containsExactly(NOW.plusSeconds(30), NOW.plusSeconds(60));
        assertThat(states.lastCode).isEqualTo("SESSION_BUSY");
    }

    @Test
    void theLastAttemptAndPermanentErrorsFail() {
        JobSearch busy = states.queued();
        workerThrowing(new JobPortalException("SESSION_BUSY", "ocupada", true)).handle(busy.id(), 3);
        JobSearch expired = states.queued();
        workerThrowing(new JobPortalException("SESSION_EXPIRED", "reconecta", false)).handle(expired.id(), 1);

        assertThat(states.failed).containsExactly("SESSION_BUSY", "SESSION_EXPIRED");
        assertThat(commands.retries).isEmpty();
    }

    @Test
    void unexpectedErrorsAreRetriedAsInternalErrors() {
        JobSearch search = states.queued();
        workerThrowing(new IllegalStateException("bug")).handle(search.id(), 1);

        assertThat(states.lastCode).isEqualTo("INTERNAL_ERROR");
        assertThat(commands.retries).containsExactly(search.id() + "#2");
    }

    @Test
    void backoffDoublesAndIsCapped() {
        assertThat(RETRIES.delayBefore(1)).isZero();
        assertThat(RETRIES.delayBefore(2)).isEqualTo(Duration.ofSeconds(30));
        assertThat(RETRIES.delayBefore(3)).isEqualTo(Duration.ofSeconds(60));
        assertThat(RETRIES.delayBefore(30)).isEqualTo(Duration.ofMinutes(10));
        assertThat(RETRIES.allowsAfter(2)).isTrue();
        assertThat(RETRIES.allowsAfter(3)).isFalse();
    }

    private JobSearchWorker worker(StubGateway gateway) {
        return new JobSearchWorker(states, new JobSearchFacade(new JobPortalGateways(List.of(gateway)), store),
                new JobSearchOutcomes(states, commands), RETRIES, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JobSearchWorker workerThrowing(RuntimeException error) {
        return worker(new StubGateway(JobPlatform.LINKEDIN, List.of()) {
            @Override
            public List<JobCard> search(UUID userId, SearchCriteria criteria) {
                throw error;
            }
        });
    }

    /** Estados en memoria con las mismas reglas que la BD: empieza solo un intento posterior al último. */
    static class FakeStates implements JobSearchStates {
        final Map<UUID, JobSearch> searches = new HashMap<>();
        final List<String> completed = new ArrayList<>();
        final List<String> failed = new ArrayList<>();
        final List<Instant> nextAttempts = new ArrayList<>();
        String lastCode;

        JobSearch queued() {
            JobSearch search = JobSearchFacadeTest.search(JobPlatform.LINKEDIN, 5);
            searches.put(search.id(), search);
            return search;
        }

        @Override
        public Optional<JobSearch> start(UUID searchId, int attempt, Instant now) {
            return Optional.ofNullable(searches.get(searchId)).map(s -> new JobSearch(s.id(), s.userId(), s.platform(),
                    s.criteria(), s.status(), attempt, s.requestedAt()));
        }

        @Override
        public void complete(UUID searchId, int found, int created, Instant now) {
            completed.add("found=" + found + " created=" + created);
        }

        @Override
        public boolean retryLater(UUID searchId, String code, String message, Instant nextAttemptAt) {
            lastCode = code;
            nextAttempts.add(nextAttemptAt);
            return true;
        }

        @Override
        public void fail(UUID searchId, String code, String message, Instant now) {
            lastCode = code;
            failed.add(code);
        }
    }

    static class FakeCommands implements JobSearchCommands {
        final List<String> retries = new ArrayList<>();

        @Override
        public void requestFirstRun(UUID searchId, UUID userId) {
        }

        @Override
        public void scheduleRetry(UUID searchId, UUID userId, int attempt) {
            retries.add(searchId + "#" + attempt);
        }
    }
}
