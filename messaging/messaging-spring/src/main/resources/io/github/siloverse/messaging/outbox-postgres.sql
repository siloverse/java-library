CREATE TABLE messaging_outbox
(
    id           BIGSERIAL PRIMARY KEY,
    message_id   UUID         NOT NULL,
    message_type VARCHAR(255) NOT NULL,
    headers      JSONB        NOT NULL,
    payload      BYTEA        NOT NULL,
    published_at TIMESTAMPTZ  NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);