-- ai-config-service (ADR 0005): configuraciones de proveedores de IA por usuario.
-- user_id es el id de identity.app_user, sin clave foránea (cada servicio es dueño de su schema).

-- ── Credenciales (API keys) cifradas con SessionCryptoService ─────────────
-- La key en claro solo existe en memoria: al recibirla del front y al entregarla al mcp-gateway.
CREATE TABLE provider_credential (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL,
    secret_encrypted    BYTEA         NOT NULL,
    iv                  BYTEA         NOT NULL,
    key_version         INTEGER       NOT NULL,
    -- Últimos 4 caracteres, para que la persona reconozca qué key guardó
    hint                VARCHAR(8)    NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_provider_credential_iv CHECK (octet_length(iv) = 12)
);
CREATE INDEX ix_provider_credential_user ON provider_credential (user_id);

-- ── Configuraciones ───────────────────────────────────────────────────────
CREATE TABLE provider_config (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL,
    provider_type       VARCHAR(32)   NOT NULL,
    display_name        VARCHAR(100)  NOT NULL,
    enabled             BOOLEAN       NOT NULL,
    model               VARCHAR(200)  NOT NULL,
    base_url            VARCHAR(2048),
    -- {"temperature": 0.7, "maxTokens": 1024, "topP": 1}
    parameters          JSONB         NOT NULL DEFAULT '{}'::jsonb,
    -- Capacidades informadas por el adaptador, modelos disponibles, etc.
    metadata            JSONB         NOT NULL DEFAULT '{}'::jsonb,
    credential_id       UUID          NOT NULL REFERENCES provider_credential (id),
    -- Versión de negocio: cada cambio la incrementa y viaja en los eventos
    version             BIGINT        NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    status_code         VARCHAR(64),
    status_message      VARCHAR(500),
    status_updated_at   TIMESTAMPTZ   NOT NULL,
    last_test_at        TIMESTAMPTZ,
    last_test_success   BOOLEAN,
    last_test_latency_ms INTEGER,
    last_test_code      VARCHAR(64),
    last_test_message   VARCHAR(500),
    last_test_correlation_id UUID,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uk_provider_config_credential UNIQUE (credential_id),
    CONSTRAINT uk_provider_config_user_name UNIQUE (user_id, display_name),
    CONSTRAINT ck_provider_config_status
        CHECK (status IN ('PENDING', 'INITIALIZING', 'ACTIVE', 'FAILED', 'DISABLED')),
    CONSTRAINT ck_provider_config_parameters CHECK (jsonb_typeof(parameters) = 'object'),
    CONSTRAINT ck_provider_config_metadata CHECK (jsonb_typeof(metadata) = 'object')
);
CREATE INDEX ix_provider_config_user ON provider_config (user_id);

-- ── Solicitudes de IA asíncronas (front → backend → RabbitMQ → mcp-gateway) ──
CREATE TABLE ai_request (
    id                  UUID          PRIMARY KEY,
    user_id             UUID          NOT NULL,
    config_id           UUID          NOT NULL REFERENCES provider_config (id) ON DELETE CASCADE,
    correlation_id      UUID          NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    output_text         TEXT,
    output_model        VARCHAR(200),
    finish_reason       VARCHAR(64),
    input_tokens        INTEGER,
    output_tokens       INTEGER,
    error_code          VARCHAR(64),
    error_message       VARCHAR(500),
    latency_ms          INTEGER,
    created_at          TIMESTAMPTZ   NOT NULL,
    deadline            TIMESTAMPTZ   NOT NULL,
    completed_at        TIMESTAMPTZ,
    CONSTRAINT ck_ai_request_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'TIMED_OUT'))
);
CREATE INDEX ix_ai_request_user ON ai_request (user_id, created_at DESC);
CREATE INDEX ix_ai_request_pending ON ai_request (deadline) WHERE status = 'PENDING';

-- ── Transactional outbox (mismo formato que identity-service) ─────────────
CREATE TABLE outbox_event (
    id              UUID          PRIMARY KEY,
    aggregate_type  VARCHAR(64)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    -- aplicafacil.events o aplicafacil.commands
    exchange        VARCHAR(64)   NOT NULL,
    event_type      VARCHAR(128)  NOT NULL,
    correlation_id  UUID,
    payload         JSONB         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at) WHERE published_at IS NULL;
