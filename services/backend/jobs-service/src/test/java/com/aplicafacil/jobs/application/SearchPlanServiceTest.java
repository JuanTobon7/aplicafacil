package com.aplicafacil.jobs.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.aplicafacil.jobs.application.SearchPlanService.SearchPlan;
import com.aplicafacil.jobs.application.port.out.FoundPosting;
import com.aplicafacil.jobs.application.port.out.JobPortalGateway;
import com.aplicafacil.jobs.application.port.out.JobSearchCommands;
import com.aplicafacil.jobs.application.port.out.JobSearchRepository;
import com.aplicafacil.jobs.domain.JobCard;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearchStatus;
import com.aplicafacil.jobs.domain.SearchCriteria;

class SearchPlanServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T12:00:00Z");

    private final FakeSearches searches = new FakeSearches();
    private final List<UUID> commanded = new ArrayList<>();
    private final SearchPlanService service = new SearchPlanService(searches, commands(),
            new JobPortalGateways(List.of(gateway(JobPlatform.COMPUTRABAJO), gateway(JobPlatform.LINKEDIN))),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void eachSearchGoesToEachPortalWithTheMainOneFirst() {
        SearchPlan plan = new SearchPlan(UUID.randomUUID(), userId, List.of("desarrollador java", "backend java"), true,
                7, null);

        assertThat(service.launch(plan)).isEqualTo(4);

        assertThat(searches.queued).extracting(q -> q.platform() + ":" + q.criteria().keywords()).containsExactly(
                "LINKEDIN:desarrollador java", "COMPUTRABAJO:desarrollador java",
                "LINKEDIN:backend java", "COMPUTRABAJO:backend java");
        assertThat(searches.queued).allSatisfy(q -> {
            assertThat(q.planId()).isEqualTo(plan.planId());
            assertThat(q.criteria().remote()).isTrue();
            assertThat(q.criteria().maxResults()).isEqualTo(SearchPlanService.MAX_RESULTS_PER_SEARCH);
        });
        assertThat(commanded).hasSize(4);
    }

    @Test
    void itStopsAtThePendingLimitAndARepeatedPlanIsIgnored() {
        searches.pending = JobSearchService.MAX_PENDING_PER_USER - 3;
        SearchPlan plan = new SearchPlan(UUID.randomUUID(), userId, List.of("java", "spring boot"), false, null,
                "Bogotá");

        assertThat(service.launch(plan)).isEqualTo(3);
        assertThat(service.launch(plan)).isZero();
        assertThat(searches.queued).hasSize(3);
    }

    private JobSearchCommands commands() {
        return new JobSearchCommands() {
            @Override
            public void requestFirstRun(UUID searchId, UUID user) {
                commanded.add(searchId);
            }

            @Override
            public void scheduleRetry(UUID searchId, UUID user, int attempt) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static JobPortalGateway gateway(JobPlatform platform) {
        return new JobPortalGateway() {
            @Override
            public JobPlatform platform() {
                return platform;
            }

            @Override
            public List<JobCard> search(UUID user, SearchCriteria criteria) {
                return List.of();
            }
        };
    }

    private record Queued(JobPlatform platform, SearchCriteria criteria, UUID planId) {
    }

    private static final class FakeSearches implements JobSearchRepository {
        final List<Queued> queued = new ArrayList<>();
        long pending;

        @Override
        public JobSearchView enqueue(UUID user, JobPlatform platform, SearchCriteria criteria, Instant now,
                                     UUID planId) {
            queued.add(new Queued(platform, criteria, planId));
            return new JobSearchView(UUID.randomUUID(), platform, criteria.keywords(), criteria.location(),
                    criteria.maxResults(), criteria.postedWithinDays(), criteria.remote(), JobSearchStatus.QUEUED, 0,
                    null, null, null, null, now, null, null, now, planId);
        }

        @Override
        public boolean planLaunched(UUID planId) {
            return queued.stream().anyMatch(q -> planId.equals(q.planId()));
        }

        @Override
        public Optional<JobSearchView> findByIdAndUserId(UUID searchId, UUID user) {
            return Optional.empty();
        }

        @Override
        public List<JobSearchView> recent(UUID user, int limit) {
            return List.of();
        }

        @Override
        public long countPending(UUID user) {
            return pending;
        }

        @Override
        public List<FoundPosting> postingsOf(UUID searchId) {
            return List.of();
        }
    }
}
