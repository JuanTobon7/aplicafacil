package com.aplicafacil.jobs.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;

import com.aplicafacil.jobs.application.JobSearchView;
import com.aplicafacil.jobs.application.port.out.FoundPosting;
import com.aplicafacil.jobs.application.port.out.JobSearchRepository;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearchStatus;
import com.aplicafacil.jobs.domain.SearchCriteria;
import com.aplicafacil.jobs.infrastructure.persistence.entity.JobSearchEntity;

/** Adaptador JPA de {@link JobSearchRepository}: la entidad no sale de infrastructure. */
@Repository
public class JpaJobSearchRepository implements JobSearchRepository {

    private static final List<JobSearchStatus> PENDING = List.of(JobSearchStatus.QUEUED, JobSearchStatus.RUNNING);

    private final SpringDataJobSearchRepository searches;
    private final SpringDataJobPostingRepository postings;

    public JpaJobSearchRepository(SpringDataJobSearchRepository searches, SpringDataJobPostingRepository postings) {
        this.searches = searches;
        this.postings = postings;
    }

    @Override
    public JobSearchView enqueue(UUID userId, JobPlatform platform, SearchCriteria criteria, Instant now,
                                 UUID planId) {
        JobSearchEntity entity = JobSearchEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .platform(platform)
                .keywords(criteria.keywords())
                .location(criteria.location())
                .maxResults(criteria.maxResults())
                .postedWithinDays(criteria.postedWithinDays())
                .remote(criteria.remote())
                .status(JobSearchStatus.QUEUED)
                .attempts(0)
                .nextAttemptAt(now)
                .requestedAt(now)
                .planId(planId)
                .build();
        return toView(searches.save(entity));
    }

    @Override
    public boolean planLaunched(UUID planId) {
        return searches.existsByPlanId(planId);
    }

    @Override
    public Optional<JobSearchView> findByIdAndUserId(UUID searchId, UUID userId) {
        return searches.findByIdAndUserId(searchId, userId).map(JpaJobSearchRepository::toView);
    }

    @Override
    public List<JobSearchView> recent(UUID userId, int limit) {
        return searches.findByUserIdOrderByRequestedAtDesc(userId, Limit.of(limit)).stream()
                .map(JpaJobSearchRepository::toView).toList();
    }

    @Override
    public long countPending(UUID userId) {
        return searches.countByUserIdAndStatusIn(userId, PENDING);
    }

    @Override
    public List<FoundPosting> postingsOf(UUID searchId) {
        return postings.findBySearch(searchId).stream().map(p -> new FoundPosting(p.getId(), p.getPlatform(),
                p.getExternalId(), p.getUrl(), p.getTitle(), p.getCompany(), p.getLocation(), p.getPostedText(),
                p.getSalaryText(), p.getQuickApply(), p.getIsNew(), p.getPosition(), p.getFirstSeenAt())).toList();
    }

    private static JobSearchView toView(JobSearchEntity e) {
        return new JobSearchView(e.getId(), e.getPlatform(), e.getKeywords(), e.getLocation(), e.getMaxResults(),
                e.getPostedWithinDays(), e.isRemote(), e.getStatus(), e.getAttempts(), e.getFoundCount(), e.getNewCount(),
                e.getErrorCode(), e.getErrorMessage(), e.getRequestedAt(), e.getStartedAt(), e.getFinishedAt(),
                e.getStatus() == JobSearchStatus.QUEUED ? e.getNextAttemptAt() : null, e.getPlanId());
    }
}
