-- computrabajo-service (ADR 0008): único dueño de la sesión de candidato de Computrabajo.
-- user_id es el id de identity.app_user; sin clave foránea entre servicios.
-- Una fila por usuario: estado de la conexión + storageState de Playwright cifrado con AES-256-GCM.
-- Desconectar = borrar la fila.
CREATE TABLE computrabajo_session (
    user_id                  UUID          PRIMARY KEY,
    status                   VARCHAR(16)   NOT NULL,
    candidate_name           VARCHAR(200),
    storage_state_encrypted  BYTEA         NOT NULL,
    iv                       BYTEA         NOT NULL,
    key_version              INTEGER       NOT NULL,
    connected_at             TIMESTAMPTZ   NOT NULL,
    last_validated_at        TIMESTAMPTZ,
    updated_at               TIMESTAMPTZ   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_computrabajo_session_status CHECK (status IN ('CONNECTED', 'EXPIRED')),
    CONSTRAINT ck_computrabajo_session_iv_length CHECK (octet_length(iv) = 12)
);
