package com.aplicafacil.jobs.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.jobs.domain.ApplicationStatus;
import com.aplicafacil.jobs.infrastructure.persistence.entity.JobApplicationEntity;

public interface SpringDataJobApplicationRepository extends JpaRepository<JobApplicationEntity, UUID> {

    /** Límite diario: postulaciones enviadas desde {@code since} (inicio del día del usuario). */
    long countByUserIdAndStatusAndAttemptedAtGreaterThanEqual(UUID userId, ApplicationStatus status, Instant since);

    void deleteByUserId(UUID userId);
}
