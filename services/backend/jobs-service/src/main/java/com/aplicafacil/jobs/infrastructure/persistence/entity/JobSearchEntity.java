package com.aplicafacil.jobs.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.aplicafacil.jobs.domain.JobPlatform;
import com.aplicafacil.jobs.domain.JobSearchStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Una búsqueda pedida por un usuario y su paso por la cola del worker. */
@Entity
@Table(name = "job_search")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobSearchEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobPlatform platform;

    @Column(nullable = false, length = 200)
    private String keywords;

    @Column(length = 120)
    private String location;

    @Column(name = "max_results", nullable = false)
    private int maxResults;

    @Column(name = "posted_within_days")
    private Integer postedWithinDays;

    @Column(nullable = false)
    private boolean remote;

    /** La tarea de IA que la lanzó (profile.search-plan.ready); null si la pidió la persona. */
    @Column(name = "plan_id")
    private UUID planId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobSearchStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "found_count")
    private Integer foundCount;

    @Column(name = "new_count")
    private Integer newCount;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
