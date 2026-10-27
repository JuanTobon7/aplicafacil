-- Registro de cada intento de postulación (ADR 0003, sección 9).
-- user_id es el id de identity.app_user; sin clave foránea entre servicios.
CREATE TABLE job_application (
    id               UUID           PRIMARY KEY,
    user_id          UUID           NOT NULL,
    job_external_id  VARCHAR(255),
    job_url          VARCHAR(2048)  NOT NULL,
    job_title        VARCHAR(255),
    company          VARCHAR(255)   NOT NULL,
    provider         VARCHAR(32)    NOT NULL,
    status           VARCHAR(32)    NOT NULL,
    error_code       VARCHAR(64),
    error_message    VARCHAR(1000),
    attempted_at     TIMESTAMPTZ    NOT NULL,
    submitted_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,
    version          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_job_application_provider
        CHECK (provider IN ('LINKEDIN_EASY_APPLY', 'GREENHOUSE', 'LEVER', 'WORKDAY')),
    CONSTRAINT ck_job_application_status
        CHECK (status IN ('PENDING_CONFIRMATION', 'IN_PROGRESS', 'SUBMITTED', 'FAILED', 'CANCELLED', 'SKIPPED'))
);
-- Historial del usuario y conteo del límite diario
CREATE INDEX ix_job_application_user_attempted ON job_application (user_id, attempted_at DESC);
-- No postular dos veces a la misma oferta
CREATE UNIQUE INDEX uk_job_application_user_job ON job_application (user_id, provider, job_external_id)
    WHERE job_external_id IS NOT NULL AND status IN ('IN_PROGRESS', 'SUBMITTED');
