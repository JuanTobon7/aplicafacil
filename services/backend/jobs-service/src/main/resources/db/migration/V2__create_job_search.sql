-- Búsqueda de vacantes (ADR 0006): una búsqueda es una tarea en cola que el worker de jobs-service
-- reclama con FOR UPDATE SKIP LOCKED; las ofertas encontradas se guardan una sola vez por portal.

-- ── Ofertas (datos públicos del portal; se comparten entre usuarios) ─────
CREATE TABLE job_posting (
    id             UUID           PRIMARY KEY,
    platform       VARCHAR(32)    NOT NULL,
    external_id    VARCHAR(64)    NOT NULL,
    url            VARCHAR(2048)  NOT NULL,
    title          VARCHAR(500)   NOT NULL,
    company        VARCHAR(255)   NOT NULL,
    location       VARCHAR(255),
    posted_text    VARCHAR(100),
    salary_text    VARCHAR(255),
    quick_apply    BOOLEAN        NOT NULL DEFAULT FALSE,
    first_seen_at  TIMESTAMPTZ    NOT NULL,
    last_seen_at   TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_job_posting_platform_external UNIQUE (platform, external_id),
    CONSTRAINT ck_job_posting_platform CHECK (platform IN ('LINKEDIN', 'COMPUTRABAJO'))
);

-- ── Búsquedas (cola de trabajo del worker) ─────────────────────────────────
CREATE TABLE job_search (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL,
    platform            VARCHAR(32)   NOT NULL,
    keywords            VARCHAR(200)  NOT NULL,
    location            VARCHAR(120),
    max_results         INTEGER       NOT NULL,
    posted_within_days  INTEGER,
    status              VARCHAR(16)   NOT NULL,
    attempts            INTEGER       NOT NULL DEFAULT 0,
    -- Cuándo puede reclamarla el worker (reintentos con espera)
    next_attempt_at     TIMESTAMPTZ   NOT NULL,
    -- Mientras corre: si el worker muere, al vencer se vuelve a encolar
    locked_until        TIMESTAMPTZ,
    found_count         INTEGER,
    new_count           INTEGER,
    error_code          VARCHAR(64),
    error_message       VARCHAR(500),
    requested_at        TIMESTAMPTZ   NOT NULL,
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    CONSTRAINT ck_job_search_platform CHECK (platform IN ('LINKEDIN', 'COMPUTRABAJO')),
    CONSTRAINT ck_job_search_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_job_search_max_results CHECK (max_results BETWEEN 1 AND 200)
);
CREATE INDEX ix_job_search_user ON job_search (user_id, requested_at DESC);
CREATE INDEX ix_job_search_due ON job_search (next_attempt_at) WHERE status = 'QUEUED';
CREATE INDEX ix_job_search_running ON job_search (locked_until) WHERE status = 'RUNNING';

-- ── Qué ofertas encontró cada búsqueda y en qué orden ──────────────────────
CREATE TABLE job_search_result (
    search_id   UUID     NOT NULL REFERENCES job_search (id) ON DELETE CASCADE,
    posting_id  UUID     NOT NULL REFERENCES job_posting (id) ON DELETE CASCADE,
    position    INTEGER  NOT NULL,
    -- La oferta no existía antes de esta búsqueda
    is_new      BOOLEAN  NOT NULL,
    PRIMARY KEY (search_id, posting_id)
);
CREATE INDEX ix_job_search_result_posting ON job_search_result (posting_id);
