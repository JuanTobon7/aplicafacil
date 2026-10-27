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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Oferta de un portal, una sola fila por (platform, external_id). Se escribe con {@code JdbcJobPostingStore}
 * (upsert); la entidad sirve para leer y validar el esquema.
 */
@Entity
@Table(name = "job_posting")
@Getter
@Setter
@NoArgsConstructor
public class JobPostingEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobPlatform platform;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private String company;

    private String location;

    @Column(name = "posted_text", length = 100)
    private String postedText;

    @Column(name = "salary_text")
    private String salaryText;

    @Column(name = "quick_apply", nullable = false)
    private boolean quickApply;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
