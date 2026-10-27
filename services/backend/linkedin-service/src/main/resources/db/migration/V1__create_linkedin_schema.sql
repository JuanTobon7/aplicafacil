-- linkedin-service (ADR 0003): único dueño de la sesión de LinkedIn.
-- user_id es el id de identity.app_user; sin clave foránea entre servicios.

-- Estado persistente de la conexión de cada usuario. CONNECTING aparece durante una reconexión;
-- el primer ingreso (todavía sin usuario) se sigue por attemptId en memoria.
CREATE TABLE linkedin_connection (
    user_id          UUID          PRIMARY KEY,
    status           VARCHAR(16)   NOT NULL,
    connection_type  VARCHAR(16)   NOT NULL,
    member_urn       VARCHAR(255)  CONSTRAINT uk_linkedin_connection_member_urn UNIQUE,
    last_error       VARCHAR(500),
    connected_at     TIMESTAMPTZ,
    last_sync_at     TIMESTAMPTZ,
    expired_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_linkedin_connection_status
        CHECK (status IN ('DISCONNECTED', 'CONNECTING', 'CONNECTED', 'EXPIRED')),
    CONSTRAINT ck_linkedin_connection_type
        CHECK (connection_type IN ('SESSION', 'OAUTH', 'EXTENSION'))
);

-- storageState de Playwright cifrado con AES-256-GCM. Es la ÚNICA ubicación persistente de la sesión.
-- Desconectar = borrar la fila.
CREATE TABLE linkedin_session (
    id                       UUID          PRIMARY KEY,
    user_id                  UUID          NOT NULL CONSTRAINT uk_linkedin_session_user UNIQUE
                                           REFERENCES linkedin_connection (user_id) ON DELETE CASCADE,
    storage_state_encrypted  BYTEA         NOT NULL,
    iv                       BYTEA         NOT NULL,
    key_version              INTEGER       NOT NULL,
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ   NOT NULL,
    last_validated_at        TIMESTAMPTZ,
    -- Bloqueo por usuario: una sola automatización a la vez sobre la misma sesión.
    -- Arrendamiento con vencimiento, así un proceso caído no deja la sesión bloqueada para siempre.
    lease_owner              UUID,
    lease_expires_at         TIMESTAMPTZ,
    version                  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_linkedin_session_iv_length CHECK (octet_length(iv) = 12),
    CONSTRAINT ck_linkedin_session_lease
        CHECK ((lease_owner IS NULL) = (lease_expires_at IS NULL))
);
