package com.aplicafacil.jobs.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import com.aplicafacil.jobs.domain.JobSearchStatus;
import com.aplicafacil.jobs.infrastructure.persistence.entity.JobSearchEntity;

public interface SpringDataJobSearchRepository extends JpaRepository<JobSearchEntity, UUID> {

    Optional<JobSearchEntity> findByIdAndUserId(UUID id, UUID userId);

    List<JobSearchEntity> findByUserIdOrderByRequestedAtDesc(UUID userId, Limit limit);

    long countByUserIdAndStatusIn(UUID userId, Collection<JobSearchStatus> statuses);

    /** user.deleted: los resultados se borran en cascada; las ofertas (públicas) se conservan. */
    void deleteByUserId(UUID userId);

    boolean existsByPlanId(UUID planId);
}
