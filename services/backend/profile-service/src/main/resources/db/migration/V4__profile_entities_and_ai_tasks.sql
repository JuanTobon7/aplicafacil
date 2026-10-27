-- ADR 0007: skills, experiencias, educación, idiomas y certificaciones pasan a ser entidades (una tabla cada una, con
-- id propio) en vez de arrays JSONB dentro de user_profile. Se copian los datos que ya había y se quitan las columnas.
-- También: título, fecha de la última importación de CV, la búsqueda de empleo del perfil, las tareas de IA y el outbox.

ALTER TABLE user_profile ADD COLUMN headline VARCHAR(255);
ALTER TABLE user_profile ADD COLUMN cv_imported_at TIMESTAMPTZ;
-- La búsqueda: las palabras clave las elige la IA; remoto, antigüedad y lugar los decide la persona
ALTER TABLE user_profile ADD COLUMN search_keywords VARCHAR(200);
ALTER TABLE user_profile ADD COLUMN search_alternatives JSONB NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE user_profile ADD COLUMN search_rationale VARCHAR(500);
ALTER TABLE user_profile ADD COLUMN search_remote_only BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_profile ADD COLUMN search_posted_within_days INTEGER
    CONSTRAINT ck_user_profile_posted_within CHECK (search_posted_within_days BETWEEN 1 AND 30);
ALTER TABLE user_profile ADD COLUMN search_location VARCHAR(120);
ALTER TABLE user_profile ADD COLUMN search_suggested_at TIMESTAMPTZ;

-- position = orden en que se muestran (lo más reciente primero, como en el CV)
CREATE TABLE profile_skill (
    id                   UUID          PRIMARY KEY,
    user_id              UUID          NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    position             INTEGER       NOT NULL,
    name                 VARCHAR(100)  NOT NULL,
    category             VARCHAR(16)   CONSTRAINT ck_profile_skill_category
                             CHECK (category IN ('LANGUAGE', 'FRAMEWORK', 'DATABASE', 'CLOUD', 'TOOL', 'PRACTICE', 'SOFT', 'OTHER')),
    level                VARCHAR(16)   CONSTRAINT ck_profile_skill_level
                             CHECK (level IN ('BASIC', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    years_of_experience  INTEGER       CONSTRAINT ck_profile_skill_years CHECK (years_of_experience BETWEEN 0 AND 60)
);
-- Sin índice único por nombre: el dominio (ProfileContent) ya une las repetidas, y reemplazar la lista inserta las
-- filas nuevas antes de borrar las viejas (orden de flush de Hibernate), lo que chocaría con ese índice.
CREATE INDEX ix_profile_skill_user ON profile_skill (user_id, position);

CREATE TABLE profile_experience (
    id            UUID           PRIMARY KEY,
    user_id       UUID           NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    position      INTEGER        NOT NULL,
    title         VARCHAR(200)   NOT NULL,
    company       VARCHAR(200)   NOT NULL,
    location      VARCHAR(200),
    -- "YYYY-MM"; end_month NULL = cargo actual
    start_month   VARCHAR(7),
    end_month     VARCHAR(7),
    description   TEXT,
    technologies  JSONB          NOT NULL DEFAULT '[]'::jsonb,
    CONSTRAINT ck_profile_experience_technologies CHECK (jsonb_typeof(technologies) = 'array')
);
CREATE INDEX ix_profile_experience_user ON profile_experience (user_id, position);

CREATE TABLE profile_education (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    position        INTEGER       NOT NULL,
    institution     VARCHAR(200)  NOT NULL,
    degree          VARCHAR(200),
    field_of_study  VARCHAR(200),
    start_month     VARCHAR(7),
    end_month       VARCHAR(7),
    description     TEXT
);
CREATE INDEX ix_profile_education_user ON profile_education (user_id, position);

CREATE TABLE profile_language (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    position     INTEGER       NOT NULL,
    name         VARCHAR(100)  NOT NULL,
    proficiency  VARCHAR(100)
);
CREATE INDEX ix_profile_language_user ON profile_language (user_id, position);

CREATE TABLE profile_certification (
    id         UUID          PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES user_profile (user_id) ON DELETE CASCADE,
    position   INTEGER       NOT NULL,
    name       VARCHAR(255)  NOT NULL,
    issuer     VARCHAR(200),
    issued_on  VARCHAR(7)
);
CREATE INDEX ix_profile_certification_user ON profile_certification (user_id, position);

-- ── Datos que ya había en los arrays JSONB ────────────────────────────────
INSERT INTO profile_skill (id, user_id, position, name)
SELECT DISTINCT ON (p.user_id, lower(btrim(s.name)))
       gen_random_uuid(), p.user_id, (s.ord - 1)::int, left(btrim(s.name), 100)
  FROM user_profile p
 CROSS JOIN LATERAL jsonb_array_elements_text(p.skills) WITH ORDINALITY AS s(name, ord)
 WHERE btrim(s.name) <> ''
 ORDER BY p.user_id, lower(btrim(s.name)), s.ord;

INSERT INTO profile_experience (id, user_id, position, title, company, location, start_month, end_month, description)
SELECT gen_random_uuid(), p.user_id, (e.ord - 1)::int, left(e.item ->> 'title', 200), left(e.item ->> 'company', 200),
       left(e.item ->> 'location', 200), left(e.item ->> 'startDate', 7), left(e.item ->> 'endDate', 7),
       e.item ->> 'description'
  FROM user_profile p
 CROSS JOIN LATERAL jsonb_array_elements(p.experience) WITH ORDINALITY AS e(item, ord)
 WHERE coalesce(btrim(e.item ->> 'title'), '') <> '' AND coalesce(btrim(e.item ->> 'company'), '') <> '';

INSERT INTO profile_education (id, user_id, position, institution, degree, field_of_study, start_month, end_month,
                               description)
SELECT gen_random_uuid(), p.user_id, (e.ord - 1)::int, left(e.item ->> 'school', 200), left(e.item ->> 'degree', 200),
       left(e.item ->> 'fieldOfStudy', 200), left(e.item ->> 'startDate', 7), left(e.item ->> 'endDate', 7),
       e.item ->> 'description'
  FROM user_profile p
 CROSS JOIN LATERAL jsonb_array_elements(p.education) WITH ORDINALITY AS e(item, ord)
 WHERE coalesce(btrim(e.item ->> 'school'), '') <> '';

INSERT INTO profile_language (id, user_id, position, name, proficiency)
SELECT gen_random_uuid(), p.user_id, (l.ord - 1)::int, left(l.item ->> 'name', 100), left(l.item ->> 'proficiency', 100)
  FROM user_profile p
 CROSS JOIN LATERAL jsonb_array_elements(p.languages) WITH ORDINALITY AS l(item, ord)
 WHERE coalesce(btrim(l.item ->> 'name'), '') <> '';

ALTER TABLE user_profile DROP CONSTRAINT ck_user_profile_arrays;
ALTER TABLE user_profile DROP COLUMN experience;
ALTER TABLE user_profile DROP COLUMN education;
ALTER TABLE user_profile DROP COLUMN skills;
ALTER TABLE user_profile DROP COLUMN languages;
ALTER TABLE user_profile ADD CONSTRAINT ck_user_profile_arrays CHECK (
    jsonb_typeof(manually_edited_fields) = 'array' AND jsonb_typeof(missing_sections) = 'array'
    AND jsonb_typeof(search_alternatives) = 'array');

-- ── Tareas de IA (su id es el requestId de ai.request.submit) ─────────────
CREATE TABLE ai_task (
    id             UUID          PRIMARY KEY,
    user_id        UUID          NOT NULL,
    config_id      UUID          NOT NULL,
    kind           VARCHAR(32)   NOT NULL CONSTRAINT ck_ai_task_kind CHECK (kind IN ('CV_EXTRACTION', 'SEARCH_SUGGESTION')),
    status         VARCHAR(16)   NOT NULL
                       CONSTRAINT ck_ai_task_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'TIMED_OUT')),
    file_name      VARCHAR(255),
    error_code     VARCHAR(64),
    error_message  VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL,
    deadline       TIMESTAMPTZ   NOT NULL,
    completed_at   TIMESTAMPTZ
);
CREATE INDEX ix_ai_task_user ON ai_task (user_id, created_at DESC);
CREATE INDEX ix_ai_task_pending ON ai_task (deadline) WHERE status = 'PENDING';

-- ── Outbox: ai.request.submit se guarda con la tarea y OutboxPublisher lo publica después ──
CREATE TABLE outbox_event (
    id              UUID          PRIMARY KEY,
    aggregate_type  VARCHAR(64)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    exchange        VARCHAR(64)   NOT NULL,
    event_type      VARCHAR(128)  NOT NULL,
    payload         JSONB         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at) WHERE published_at IS NULL;
