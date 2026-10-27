-- identity-service (ADR 0003): LinkedIn es el registro y el login.
-- Los demás servicios referencian app_user.id solo como UUID, sin clave foránea.

-- ── Usuarios ──────────────────────────────────────────────────────────────
CREATE TABLE app_user (
    id                   UUID          PRIMARY KEY,
    -- Fase local (sesión de Playwright): urn:li:fsd_profile:...
    linkedin_member_urn  VARCHAR(255)  CONSTRAINT uk_app_user_member_urn UNIQUE,
    -- Fase desplegada (OpenID Connect): sub del userinfo
    linkedin_oidc_sub    VARCHAR(255)  CONSTRAINT uk_app_user_oidc_sub UNIQUE,
    email                VARCHAR(320),
    email_verified       BOOLEAN       NOT NULL DEFAULT FALSE,
    first_name           VARCHAR(100),
    last_name            VARCHAR(100),
    headline             VARCHAR(500),
    photo_url            VARCHAR(2048),
    public_profile_url   VARCHAR(2048),
    location             VARCHAR(255),
    -- Campos básicos que el usuario editó en la plataforma: una sincronización no los pisa.
    -- Ej.: ["headline", "location"]
    edited_fields        JSONB         NOT NULL DEFAULT '[]'::jsonb,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL,
    last_login_at        TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_app_user_linkedin_id
        CHECK (linkedin_member_urn IS NOT NULL OR linkedin_oidc_sub IS NOT NULL),
    CONSTRAINT ck_app_user_edited_fields CHECK (jsonb_typeof(edited_fields) = 'array')
);
-- Clave secundaria para vincular una cuenta creada en un modo con la del otro:
-- solo correos verificados, sin distinguir mayúsculas.
CREATE UNIQUE INDEX uk_app_user_verified_email ON app_user (lower(email)) WHERE email_verified;

-- ── Refresh tokens (cookie af_rt) ─────────────────────────────────────────
-- Opacos y rotativos: solo se guarda el SHA-256. Reusar uno ya rotado revoca toda la familia.
CREATE TABLE refresh_token (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash   BYTEA        NOT NULL CONSTRAINT uk_refresh_token_hash UNIQUE,
    family_id    UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID
);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_refresh_token_family ON refresh_token (family_id);

-- ── Códigos de inicio de sesión de un solo uso ────────────────────────────
-- linkedin-service los recibe al provisionar el usuario y el front los canjea por tokens
-- (así ningún token viaja por SSE ni por la URL).
CREATE TABLE login_code (
    -- SHA-256 en hexadecimal (el código en claro solo existe en la respuesta a linkedin-service)
    code_hash    CHAR(64)     PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    attempt_id   UUID         NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    consumed_at  TIMESTAMPTZ
);
CREATE INDEX ix_login_code_user ON login_code (user_id);

-- ── Tokens OAuth de LinkedIn (fase desplegada), cifrados con SessionCryptoService ──
CREATE TABLE oauth_token (
    id                        UUID          PRIMARY KEY,
    user_id                   UUID          NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider                  VARCHAR(32)   NOT NULL,
    access_token_encrypted    BYTEA         NOT NULL,
    access_token_iv           BYTEA         NOT NULL,
    refresh_token_encrypted   BYTEA,
    refresh_token_iv          BYTEA,
    key_version               INTEGER       NOT NULL,
    scope                     VARCHAR(255),
    access_token_expires_at   TIMESTAMPTZ   NOT NULL,
    refresh_token_expires_at  TIMESTAMPTZ,
    created_at                TIMESTAMPTZ   NOT NULL,
    updated_at                TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_oauth_token_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_oauth_token_refresh_iv
        CHECK ((refresh_token_encrypted IS NULL) = (refresh_token_iv IS NULL))
);

-- ── Llaves de firma RS256 de los JWT propios ──────────────────────────────
-- La privada va cifrada; la pública se publica en el JWKS. Solo una activa a la vez.
CREATE TABLE signing_key (
    kid                    VARCHAR(64)  PRIMARY KEY,
    algorithm              VARCHAR(16)  NOT NULL,
    public_key             BYTEA        NOT NULL,
    private_key_encrypted  BYTEA        NOT NULL,
    private_key_iv         BYTEA        NOT NULL,
    key_version            INTEGER      NOT NULL,
    active                 BOOLEAN      NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    retired_at             TIMESTAMPTZ
);
CREATE UNIQUE INDEX uk_signing_key_single_active ON signing_key (active) WHERE active;

-- ── Transactional outbox (user.deleted, user.provisioned) ─────────────────
CREATE TABLE outbox_event (
    id              UUID          PRIMARY KEY,
    aggregate_type  VARCHAR(64)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(128)  NOT NULL,
    payload         JSONB         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at) WHERE published_at IS NULL;
