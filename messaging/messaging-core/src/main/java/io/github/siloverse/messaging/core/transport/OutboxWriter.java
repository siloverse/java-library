package io.github.siloverse.messaging.core.transport;

/**
 * Appends an outgoing envelope to the outbox, inside whatever transaction the caller is
 * running -- commit keeps the row, rollback removes it. Core never manages the transaction;
 * implementations (e.g. a JDBC insert in messaging-spring) inherit the caller's.
 *
 * <p>Write-only by contract: the relay reads and stamps the outbox table by its own means.
 * Outbox knowledge ends at broker ack -- do not add read or status methods here.
 */
public interface OutboxWriter {
    void append(Envelope envelope);
}
