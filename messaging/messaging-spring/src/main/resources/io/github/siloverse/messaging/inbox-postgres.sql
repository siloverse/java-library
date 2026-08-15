-- Deduplication ledger for @Consumer(dedup = true) handlers.
-- NOT a store of incoming messages: one row = "this consumer has fully processed this
-- message". The composite PRIMARY KEY IS the dedup mechanism -- races between concurrent
-- duplicate deliveries are arbitrated by the index, never by application logic.
-- Lives in the CONSUMER's database: the row is inserted inside the business transaction,
-- so ledger entry and business effects commit or roll back together.
CREATE TABLE IF NOT EXISTS messaging_inbox (
    consumer_id  varchar     NOT NULL,  -- @Consumer id: dedup is per consumer, not per message
    message_id   uuid        NOT NULL,  -- wire identity, minted once by the publishing bus
    processed_at timestamptz NOT NULL DEFAULT now(),  -- diagnostics + future retention janitor
    PRIMARY KEY (consumer_id, message_id)
);
