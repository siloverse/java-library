package io.github.siloverse.messaging.spring.inbox;

import java.util.Objects;
import java.util.UUID;

import io.github.siloverse.messaging.core.consumer.Inbox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link Inbox} over the consumer's own database (DDL shipped as
 * {@code io/github/siloverse/messaging/inbox-postgres.sql} -- schema ownership belongs to
 * the application, same rule as the outbox).
 *
 * <p>One transaction holds the ledger insert and the business invocation.
 * {@code ON CONFLICT DO NOTHING} makes the duplicate check race-safe without aborting the
 * transaction (Postgres aborts a tx on a raised unique violation; the conflict clause
 * reports it as an update count of zero instead). A concurrent duplicate blocks on the
 * index entry until the first transaction resolves, then reads the truth.
 */
public class JdbcInbox implements Inbox {

    private static final String RECORD_SQL = """
            INSERT INTO messaging_inbox (consumer_id, message_id)
            VALUES (?, ?)
            ON CONFLICT DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcInbox(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
    }

    @Override
    public boolean processOnce(String consumerId, UUID messageId, Runnable invocation) {
        Objects.requireNonNull(consumerId, "consumerId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(invocation, "invocation must not be null");

        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            int inserted = jdbcTemplate.update(RECORD_SQL, consumerId, messageId);
            if (inserted == 0) {
                return false; // already processed: skip the invocation, commit the empty tx
            }
            invocation.run(); // throws -> tx rolls back, ledger entry disappears with it
            return true;
        }));
    }
}
