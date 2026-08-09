package io.github.siloverse.messaging.persistence.repository;

import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Access to the {@code message_deliveries} table.
 *
 * <p>Claiming deliveries is deliberately absent here: it needs {@code FOR UPDATE SKIP LOCKED} and
 * lives behind {@link io.github.siloverse.messaging.persistence.DeliveryClaimStrategy}.
 */
public interface MessageDeliveryRepository extends JpaRepository<MessageDelivery, UUID> {

    /**
     * @param messageId the message
     * @return every delivery created for that message
     */
    List<MessageDelivery> findAllByMessageId(UUID messageId);

    /**
     * @param status the status to count
     * @return how many deliveries currently have it
     */
    long countByStatus(DeliveryStatus status);

    /**
     * Gives up on deliveries whose worker disappeared and which have no attempts left.
     *
     * <p>Run before {@link #rescheduleAbandoned} so that a delivery is considered only once.
     *
     * @param cutoff      deliveries locked before this instant count as abandoned
     * @param maxAttempts the configured attempt limit
     * @param now         the instant recorded as the final failure
     * @param error       the error text to store
     * @return how many rows were failed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MessageDelivery d
               set d.status = io.github.siloverse.messaging.persistence.entity.DeliveryStatus.FAILED,
                   d.lockedAt = null,
                   d.processedAt = :now,
                   d.lastError = :error
             where d.status = io.github.siloverse.messaging.persistence.entity.DeliveryStatus.PROCESSING
               and d.lockedAt < :cutoff
               and d.attempts >= :maxAttempts
            """)
    int failAbandoned(@Param("cutoff") Instant cutoff,
                      @Param("maxAttempts") int maxAttempts,
                      @Param("now") Instant now,
                      @Param("error") String error);

    /**
     * Returns deliveries whose worker disappeared to the queue.
     *
     * @param cutoff      deliveries locked before this instant count as abandoned
     * @param maxAttempts the configured attempt limit
     * @param availableAt when the reclaimed deliveries become eligible again
     * @param error       the error text to store
     * @return how many rows were rescheduled
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update MessageDelivery d
               set d.status = io.github.siloverse.messaging.persistence.entity.DeliveryStatus.PENDING,
                   d.lockedAt = null,
                   d.availableAt = :availableAt,
                   d.lastError = :error
             where d.status = io.github.siloverse.messaging.persistence.entity.DeliveryStatus.PROCESSING
               and d.lockedAt < :cutoff
               and d.attempts < :maxAttempts
            """)
    int rescheduleAbandoned(@Param("cutoff") Instant cutoff,
                            @Param("maxAttempts") int maxAttempts,
                            @Param("availableAt") Instant availableAt,
                            @Param("error") String error);
}
