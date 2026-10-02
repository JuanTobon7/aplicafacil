-- ═════════════════════════ Usuarios ═════════════════════════
CREATE TABLE users (
    id                    UUID         PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL CONSTRAINT uk_users_email UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER      NOT NULL DEFAULT 0 CONSTRAINT ck_users_failed CHECK (failed_login_attempts >= 0),
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role    VARCHAR(20) NOT NULL CONSTRAINT ck_user_roles_role CHECK (role IN ('USER', 'ADMIN')),
    PRIMARY KEY (user_id, role)
);

-- ═════════════════════════ Llaves de firma (JWK) ═════════════════════════
-- Persistidas para que los tokens sigan siendo válidos tras un reinicio y
-- todas las instancias firmen con la misma llave. Se publican TODAS en
-- /oauth2/jwks (rotación) y se firma con la activa más reciente.
CREATE TABLE signing_keys (
    kid         VARCHAR(100) PRIMARY KEY,
    algorithm   VARCHAR(20)  NOT NULL,
    public_key  TEXT         NOT NULL,
    private_key TEXT         NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ═════════════════════════ Spring Authorization Server ═════════════════════════
-- Esquemas oficiales (spring-security-oauth2-authorization-server 7.1) adaptados
-- a PostgreSQL: blob → text, timestamp → timestamptz.
CREATE TABLE oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL PRIMARY KEY,
    client_id                     VARCHAR(100)  NOT NULL CONSTRAINT uk_oauth2_registered_client_client_id UNIQUE,
    client_id_issued_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_secret                 VARCHAR(200)  DEFAULT NULL,
    client_secret_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL
);

CREATE TABLE oauth2_authorization (
    id                            VARCHAR(100)  NOT NULL PRIMARY KEY,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      TEXT          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_expires_at TIMESTAMPTZ   DEFAULT NULL,
    authorization_code_metadata   TEXT          DEFAULT NULL,
    access_token_value            TEXT          DEFAULT NULL,
    access_token_issued_at        TIMESTAMPTZ   DEFAULT NULL,
    access_token_expires_at       TIMESTAMPTZ   DEFAULT NULL,
    access_token_metadata         TEXT          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    oidc_id_token_metadata        TEXT          DEFAULT NULL,
    refresh_token_value           TEXT          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMPTZ   DEFAULT NULL,
    refresh_token_metadata        TEXT          DEFAULT NULL,
    user_code_value               TEXT          DEFAULT NULL,
    user_code_issued_at           TIMESTAMPTZ   DEFAULT NULL,
    user_code_expires_at          TIMESTAMPTZ   DEFAULT NULL,
    user_code_metadata            TEXT          DEFAULT NULL,
    device_code_value             TEXT          DEFAULT NULL,
    device_code_issued_at         TIMESTAMPTZ   DEFAULT NULL,
    device_code_expires_at        TIMESTAMPTZ   DEFAULT NULL,
    device_code_metadata          TEXT          DEFAULT NULL
);
-- Búsquedas por igualdad de token / state (canje de code, introspección,
-- revocación, token exchange). HASH: los JWT pueden superar el límite de btree.
CREATE INDEX idx_oauth2_authorization_state ON oauth2_authorization USING HASH (state);
CREATE INDEX idx_oauth2_authorization_code ON oauth2_authorization USING HASH (authorization_code_value);
CREATE INDEX idx_oauth2_authorization_access_token ON oauth2_authorization USING HASH (access_token_value);
CREATE INDEX idx_oauth2_authorization_refresh_token ON oauth2_authorization USING HASH (refresh_token_value);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100)  NOT NULL,
    principal_name       VARCHAR(200)  NOT NULL,
    authorities          VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
