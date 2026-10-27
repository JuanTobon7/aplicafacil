-- ADR 0003: el perfil se llena desde LinkedIn y se guarda como un documento por usuario.
-- Reemplaza el agregado relacional de V2 (sin API ni datos reales todavía; el código anterior está en legacy/).
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS experiences;
DROP TABLE IF EXISTS education;
DROP TABLE IF EXISTS skills;
DROP TABLE IF EXISTS profiles;
DROP TABLE IF EXISTS cvs;

-- user_id es el id de identity.app_user; sin clave foránea entre servicios.
CREATE TABLE user_profile (
    user_id                 UUID         PRIMARY KEY,
    summary                 TEXT,
    -- [{title, company, startDate "YYYY-MM", endDate|null, location, description}]
    experience              JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- [{school, degree, fieldOfStudy, startDate, endDate, description}]
    education               JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- ["Java", "Spring", ...]
    skills                  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- [{name, proficiency}]
    languages               JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- Secciones editadas a mano por el usuario: la sincronización no las sobrescribe.
    -- Valores: SUMMARY, EXPERIENCE, EDUCATION, SKILLS, LANGUAGES
    manually_edited_fields  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    -- Lectura parcial: el usuario existe, pero faltan secciones por reintentar
    complete                BOOLEAN      NOT NULL DEFAULT FALSE,
    missing_sections        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    last_synced_at          TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_user_profile_arrays CHECK (
        jsonb_typeof(experience) = 'array' AND jsonb_typeof(education) = 'array'
        AND jsonb_typeof(skills) = 'array' AND jsonb_typeof(languages) = 'array'
        AND jsonb_typeof(manually_edited_fields) = 'array' AND jsonb_typeof(missing_sections) = 'array')
);
