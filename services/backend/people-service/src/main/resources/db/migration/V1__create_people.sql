-- Personas (candidatos). Los perfiles y usuarios viven en otros servicios y
-- referencian a la persona por id: no hay claves foráneas hacia afuera.
CREATE TABLE people (
    id            UUID         PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    phone         VARCHAR(255),
    linkedin_url  VARCHAR(255),
    github_url    VARCHAR(255),
    portfolio_url VARCHAR(255),
    resume_url    VARCHAR(255),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_people_email UNIQUE (email)
);
