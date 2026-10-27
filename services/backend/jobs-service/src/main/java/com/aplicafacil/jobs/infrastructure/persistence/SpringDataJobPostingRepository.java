package com.aplicafacil.jobs.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.infrastructure.persistence.entity.JobPostingEntity;

public interface SpringDataJobPostingRepository extends JpaRepository<JobPostingEntity, UUID> {

    /** Las ofertas de una búsqueda, en el orden en que las mostró el portal. */
    @Query(value = """
            SELECT p.id AS id, p.platform AS platform, p.external_id AS externalId, p.url AS url, p.title AS title,
                   p.company AS company, p.location AS location, p.posted_text AS postedText,
                   p.salary_text AS salaryText, p.quick_apply AS quickApply, r.is_new AS isNew,
                   r.position AS position, p.first_seen_at AS firstSeenAt
              FROM jobs.job_search_result r
              JOIN jobs.job_posting p ON p.id = r.posting_id
             WHERE r.search_id = :searchId
             ORDER BY r.position
            """, nativeQuery = true)
    List<FoundPostingRow> findBySearch(UUID searchId);

    interface FoundPostingRow {
        UUID getId();

        JobPlatform getPlatform();

        String getExternalId();

        String getUrl();

        String getTitle();

        String getCompany();

        String getLocation();

        String getPostedText();

        String getSalaryText();

        boolean getQuickApply();

        boolean getIsNew();

        int getPosition();

        Instant getFirstSeenAt();
    }
}
