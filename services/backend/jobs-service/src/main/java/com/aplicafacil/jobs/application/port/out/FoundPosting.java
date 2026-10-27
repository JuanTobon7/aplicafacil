package com.aplicafacil.jobs.application.port.out;

import java.time.Instant;
import java.util.UUID;

import com.aplicafacil.jobs.domain.JobPlatform;

/** Una oferta tal como apareció en una búsqueda ({@code isNew}: la búsqueda la descubrió). */
public record FoundPosting(UUID id, JobPlatform platform, String externalId, String url, String title, String company,
                           String location, String postedText, String salaryText, boolean quickApply, boolean isNew,
                           int position, Instant firstSeenAt) {
}
