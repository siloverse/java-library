package io.github.siloverse.messaging.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Claims eligible deliveries for the calling application instance.
 *
 * <p>This is the one place that depends on database specific locking. Claiming must be atomic
 * enough that two poller threads, in one process or across several instances, never hand the same
 * delivery to a worker at the same time.
 */
public interface DeliveryClaimStrategy {

    /**
     * Marks up to {@code batchSize} eligible deliveries as {@code PROCESSING} and returns their
     * ids. Must be called inside a transaction; the claim is visible to other instances only once
     * that transaction commits.
     *
     * @param batchSize   the maximum number of deliveries to claim
     * @param now         the current instant, used for eligibility and as lock timestamp
     * @param maxAttempts the configured attempt limit
     * @return the ids of the claimed deliveries, possibly empty
     */
    List<UUID> claim(int batchSize, Instant now, int maxAttempts);
}
