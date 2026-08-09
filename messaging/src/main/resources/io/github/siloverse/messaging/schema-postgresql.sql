-- Schema for the io.github.siloverse.messaging durable message tables (PostgreSQL).
--
-- Apply this through the application's migration tool (Flyway, Liquibase, ...) in production.
-- Setting messaging.schema.initialize=true runs it on startup instead, which suits tests and
-- local development. The script is idempotent so either route can be repeated safely.

CREATE TABLE IF NOT EXISTS messages
(
    id           UUID         NOT NULL PRIMARY KEY,
    message_type VARCHAR(500) NOT NULL,
    message_kind VARCHAR(20)  NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS message_deliveries
(
    id           UUID         NOT NULL PRIMARY KEY,
    message_id   UUID         NOT NULL REFERENCES messages (id),
    consumer_id  VARCHAR(500) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    attempts     INTEGER      NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at    TIMESTAMP WITH TIME ZONE,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_error   TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Serves the claim query:
--   WHERE status = 'PENDING' AND available_at <= now AND attempts < max
--   ORDER BY available_at, created_at
--   FOR UPDATE SKIP LOCKED
-- Partial, because only PENDING rows are ever claimed and processed rows accumulate.
CREATE INDEX IF NOT EXISTS idx_message_deliveries_claim
    ON message_deliveries (available_at, created_at)
    WHERE status = 'PENDING';

-- Serves the stale lock recovery queries:
--   WHERE status = 'PROCESSING' AND locked_at < cutoff
CREATE INDEX IF NOT EXISTS idx_message_deliveries_locked
    ON message_deliveries (locked_at)
    WHERE status = 'PROCESSING';

-- Serves lookups of every delivery of one message.
CREATE INDEX IF NOT EXISTS idx_message_deliveries_message
    ON message_deliveries (message_id);
