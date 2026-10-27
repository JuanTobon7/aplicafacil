-- ADR 0006: las búsquedas se reparten por RabbitMQ (jobs.search.requested), no sondeando esta tabla. El candado
-- (locked_until) lo reemplaza la reentrega de mensajes sin confirmar, y los índices del sondeo ya no se usan.
DROP INDEX IF EXISTS ix_job_search_due;
DROP INDEX IF EXISTS ix_job_search_running;
ALTER TABLE job_search DROP COLUMN locked_until;

-- Outbox: el comando se guarda en la misma transacción que la búsqueda y OutboxPublisher lo publica después.
CREATE TABLE outbox_event (
    id              UUID          PRIMARY KEY,
    aggregate_type  VARCHAR(64)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    -- aplicafacil.events o aplicafacil.commands
    exchange        VARCHAR(64)   NOT NULL,
    -- routing key
    event_type      VARCHAR(128)  NOT NULL,
    payload         JSONB         NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER       NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at) WHERE published_at IS NULL;
