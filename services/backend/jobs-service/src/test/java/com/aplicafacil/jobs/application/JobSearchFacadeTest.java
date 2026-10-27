package com.aplicafacil.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.jobs.application.JobSearchFacade.Outcome;
import com.aplicafacil.jobs.application.port.out.JobPortalException;
import com.aplicafacil.jobs.application.port.out.JobPortalGateway;
import com.aplicafacil.jobs.application.port.out.JobPostingStore;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearch;
import com.aplicafacil.jobs.domain.JobSearchStatus;
import com.aplicafacil.jobs.domain.SearchCriteria;

/** La fachada elige la estrategia del portal, limpia las cards y las guarda. */
class JobSearchFacadeTest {

    private final RecordingStore store = new RecordingStore();

    @Test
    void usesTheGatewayOfThePlatformDedupesAndPersists() {
        StubGateway linkedin = new StubGateway(JobPlatform.LINKEDIN,
                List.of(card("1"), card("2"), card("1"), incomplete("3"), card("4")));
        StubGateway computrabajo = new StubGateway(JobPlatform.COMPUTRABAJO, List.of(card("x")));
        JobSearchFacade facade = new JobSearchFacade(new JobPortalGateways(List.of(linkedin, computrabajo)), store);

        Outcome outcome = facade.run(search(JobPlatform.LINKEDIN, 10));

        assertThat(linkedin.calls).isEqualTo(1);
        assertThat(computrabajo.calls).isZero();
        assertThat(store.saved).extracting(JobCard::externalId).containsExactly("1", "2", "4");
        assertThat(store.platform).isEqualTo(JobPlatform.LINKEDIN);
        assertThat(outcome).isEqualTo(new Outcome(3, 3));
    }

    @Test
    void respectsMaxResultsAndSkipsTheStoreWhenNothingWasFound() {
        StubGateway gateway = new StubGateway(JobPlatform.COMPUTRABAJO, List.of(card("1"), card("2"), card("3")));
        JobSearchFacade facade = new JobSearchFacade(new JobPortalGateways(List.of(gateway)), store);

        assertThat(facade.run(search(JobPlatform.COMPUTRABAJO, 2)).found()).isEqualTo(2);

        gateway.cards = List.of();
        store.saved = null;
        assertThat(facade.run(search(JobPlatform.COMPUTRABAJO, 2))).isEqualTo(new Outcome(0, 0));
        assertThat(store.saved).isNull();
    }

    @Test
    void anUnsupportedPlatformIsAPermanentFailure() {
        JobSearchFacade facade = new JobSearchFacade(new JobPortalGateways(List.of()), store);
        assertThatThrownBy(() -> facade.run(search(JobPlatform.LINKEDIN, 5)))
                .isInstanceOfSatisfying(JobPortalException.class, e -> {
                    assertThat(e.code()).isEqualTo("UNSUPPORTED_PLATFORM");
                    assertThat(e.retryable()).isFalse();
                });
    }

    @Test
    void twoGatewaysForTheSamePlatformAreAConfigurationError() {
        assertThatThrownBy(() -> new JobPortalGateways(List.of(
                new StubGateway(JobPlatform.LINKEDIN, List.of()), new StubGateway(JobPlatform.LINKEDIN, List.of()))))
                .isInstanceOf(IllegalStateException.class);
    }

    static JobSearch search(JobPlatform platform, int maxResults) {
        return new JobSearch(UUID.randomUUID(), UUID.randomUUID(), platform,
                new SearchCriteria("java", null, maxResults, null), JobSearchStatus.RUNNING, 1, Instant.now());
    }

    static JobCard card(String id) {
        return new JobCard(id, "https://portal.example/jobs/" + id, "Oferta " + id, "Acme", null, null, null, false);
    }

    static JobCard incomplete(String id) {
        return new JobCard(id, "https://portal.example/jobs/" + id, " ", "Acme", null, null, null, false);
    }

    static class StubGateway implements JobPortalGateway {
        private final JobPlatform platform;
        List<JobCard> cards;
        int calls;

        StubGateway(JobPlatform platform, List<JobCard> cards) {
            this.platform = platform;
            this.cards = cards;
        }

        @Override
        public JobPlatform platform() {
            return platform;
        }

        @Override
        public List<JobCard> search(UUID userId, SearchCriteria criteria) {
            calls++;
            return cards;
        }
    }

    static class RecordingStore implements JobPostingStore {
        List<JobCard> saved;
        JobPlatform platform;

        @Override
        public int saveAll(UUID searchId, JobPlatform platform, List<JobCard> cards) {
            this.saved = new ArrayList<>(cards);
            this.platform = platform;
            return cards.size();
        }
    }
}
