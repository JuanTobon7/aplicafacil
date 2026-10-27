package com.aplicafacil.jobs.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.aplicafacil.jobs.domain.ApplicationProviderType;
import com.aplicafacil.jobs.domain.ApplicationStatus;

import lombok.Getter;
import lombok.Setter;

/** Un intento de postulación: usuario, oferta, empresa, proveedor, estado, error y fecha. */
@Entity
@Table(name = "job_application")
@Getter
@Setter
public class JobApplicationEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "job_external_id")
    private String jobExternalId;

    @Column(name = "job_url", nullable = false, length = 2048)
    private String jobUrl;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(nullable = false)
    private String company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
