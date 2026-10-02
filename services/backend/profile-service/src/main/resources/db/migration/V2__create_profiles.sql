-- Agregado Profile: perfil + skills, experiencias, educación, proyectos y CV.
-- person_id referencia a people-service: es solo un id, sin clave foránea
-- (cada microservicio es dueño de sus datos).

CREATE TABLE cvs (
    id        UUID          PRIMARY KEY,
    file_path VARCHAR(1024) NOT NULL,
    mime_type VARCHAR(255)  NOT NULL
);

CREATE TABLE profiles (
    id        UUID         PRIMARY KEY,
    person_id UUID         NOT NULL,
    title     VARCHAR(255) NOT NULL,
    summary   TEXT         NOT NULL,
    -- 1:1 con cvs, el perfil posee la relación (igual que el modelo original)
    cv_id     UUID         CONSTRAINT uk_profiles_cv UNIQUE
                           CONSTRAINT fk_profiles_cv REFERENCES cvs (id) ON DELETE SET NULL
);
CREATE INDEX idx_profiles_person_id ON profiles (person_id);

-- Los hijos se eliminan con su perfil (ON DELETE CASCADE), igual que hace JPA con
-- cascade + orphanRemoval. El modelo original solo lo declaraba en experiences;
-- aquí se aplica a todos porque ninguno tiene sentido sin su perfil.

CREATE TABLE skills (
    id                  UUID         PRIMARY KEY,
    profile_id          UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    description         VARCHAR(255),
    years_of_experience INTEGER      CONSTRAINT ck_skills_years CHECK (years_of_experience >= 0)
);
CREATE INDEX idx_skills_profile_id ON skills (profile_id);

CREATE TABLE experiences (
    id           UUID         PRIMARY KEY,
    profile_id   UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    company_name VARCHAR(100) NOT NULL,
    position     VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    start_date   DATE         NOT NULL,
    end_date     DATE,
    CONSTRAINT ck_experiences_period CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_experiences_profile_id ON experiences (profile_id);

CREATE TABLE education (
    id               UUID         PRIMARY KEY,
    profile_id       UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    institution_name VARCHAR(100) NOT NULL,
    description      VARCHAR(250),
    start_date       DATE         NOT NULL,
    end_date         DATE,
    CONSTRAINT ck_education_period CHECK (end_date IS NULL OR end_date >= start_date)
);
CREATE INDEX idx_education_profile_id ON education (profile_id);

CREATE TABLE projects (
    id           UUID         PRIMARY KEY,
    profile_id   UUID         NOT NULL REFERENCES profiles (id) ON DELETE CASCADE,
    name         VARCHAR(255) NOT NULL,
    description  TEXT         NOT NULL,
    technologies VARCHAR(255)
);
CREATE INDEX idx_projects_profile_id ON projects (profile_id);
