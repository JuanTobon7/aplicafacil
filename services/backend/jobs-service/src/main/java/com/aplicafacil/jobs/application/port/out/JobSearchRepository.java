package com.aplicafacil.jobs.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.aplicafacil.jobs.application.JobSearchView;
import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.SearchCriteria;

/** Las búsquedas pedidas por el front. Encolarlas es otro puerto ({@link JobSearchCommands}). */
public interface JobSearchRepository {

    /** La deja QUEUED para que el worker la tome en {@code now}. */
    JobSearchView enqueue(UUID userId, JobPlatform platform, SearchCriteria criteria, Instant now);

    Optional<JobSearchView> findByIdAndUserId(UUID searchId, UUID userId);

    /** Las más recientes primero. */
    List<JobSearchView> recent(UUID userId, int limit);

    /** QUEUED o RUNNING. */
    long countPending(UUID userId);

    /** Las ofertas de una búsqueda, en el orden en que las mostró el portal. */
    List<FoundPosting> postingsOf(UUID searchId);
}
