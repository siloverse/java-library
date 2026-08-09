package io.github.siloverse.messaging.persistence;

import io.github.siloverse.messaging.exception.TransactionRequiredException;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Claims deliveries with {@code SELECT ... FOR UPDATE SKIP LOCKED}.
 *
 * <p>Rows already locked by another transaction are skipped rather than waited for, so several
 * pollers make progress in parallel without ever seeing the same row. Once the claiming transaction
 * commits, the rows are {@code PROCESSING} and no longer match the eligibility predicate, so they
 * stay invisible to other instances even after the row locks are released.
 *
 * <p>Requires a database supporting {@code SKIP LOCKED}: PostgreSQL 9.5+, MySQL 8+, Oracle,
 * MariaDB 10.6+. This is the only class containing dialect specific SQL.
 */
public class SkipLockedDeliveryClaimStrategy implements DeliveryClaimStrategy {

    private static final String SELECT_ELIGIBLE = """
            select id from message_deliveries
             where status = ?
               and available_at <= ?
               and attempts < ?
             order by available_at, created_at
             limit ?
             for update skip locked
            """;

    private static final String CLAIM_TEMPLATE = """
            update message_deliveries
               set status = ?, locked_at = ?, attempts = attempts + 1
             where id in (%s)
            """;

    private final JdbcOperations jdbc;

    public SkipLockedDeliveryClaimStrategy(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> claim(int batchSize, Instant now, int maxAttempts) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new TransactionRequiredException(
                    "Deliveries must be claimed inside a transaction, otherwise the row locks are "
                            + "released before the claim is recorded.");
        }
        if (batchSize <= 0) {
            return List.of();
        }

        // Bound as an offset date time so the driver sends a real timestamptz value instead of
        // letting the database reinterpret a local timestamp in the session time zone.
        OffsetDateTime timestamp = now.atOffset(ZoneOffset.UTC);
        List<UUID> ids = jdbc.query(
                SELECT_ELIGIBLE,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                DeliveryStatus.PENDING.name(), timestamp, maxAttempts, batchSize);

        if (ids.isEmpty()) {
            return List.of();
        }

        String placeholders = ids.stream().map(id -> "?").collect(Collectors.joining(", "));
        Object[] arguments = new Object[ids.size() + 2];
        arguments[0] = DeliveryStatus.PROCESSING.name();
        arguments[1] = timestamp;
        for (int i = 0; i < ids.size(); i++) {
            arguments[i + 2] = ids.get(i);
        }
        jdbc.update(CLAIM_TEMPLATE.formatted(placeholders), arguments);

        return ids;
    }
}
