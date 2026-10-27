package com.aplicafacil.jobs.application;

import java.time.Instant;
import java.util.UUID;

import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearchStatus;

/** Estado de una búsqueda para el front ({@code nextAttemptAt} solo mientras espera en la cola). */
public record JobSearchView(UUID id, JobPlatform platform, String keywords, String location, int maxResults,
                            Integer postedWithinDays, boolean remote, JobSearchStatus status, int attempts, Integer foundCount,
                            Integer newCount, String errorCode, String errorMessage, Instant requestedAt,
                            Instant startedAt, Instant finishedAt, Instant nextAttemptAt, UUID planId) {
}
