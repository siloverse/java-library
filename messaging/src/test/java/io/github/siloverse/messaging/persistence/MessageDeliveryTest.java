package io.github.siloverse.messaging.persistence;

import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * State transitions of a delivery row, independent of any database.
 */
class MessageDeliveryTest {

    private final Instant now = Instant.parse("2026-08-08T10:15:30Z");

    @Test
    void aNewDeliveryIsPendingAndImmediatelyEligible() {
        MessageDelivery delivery = newDelivery();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.getAvailableAt()).isEqualTo(now);
        assertThat(delivery.getLockedAt()).isNull();
        assertThat(delivery.getProcessedAt()).isNull();
        assertThat(delivery.getLastError()).isNull();
    }

    @Test
    void markProcessedClearsTheLockAndTheLastError() {
        MessageDelivery delivery = newDelivery();
        delivery.markForRetry(now.plusSeconds(5), "boom");

        delivery.markProcessed(now.plusSeconds(10));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PROCESSED);
        assertThat(delivery.getProcessedAt()).isEqualTo(now.plusSeconds(10));
        assertThat(delivery.getLockedAt()).isNull();
        assertThat(delivery.getLastError()).isNull();
    }

    @Test
    void markForRetryMovesTheDeliveryBackToPendingWithTheError() {
        MessageDelivery delivery = newDelivery();

        delivery.markForRetry(now.plusSeconds(30), "boom");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getAvailableAt()).isEqualTo(now.plusSeconds(30));
        assertThat(delivery.getLastError()).isEqualTo("boom");
        assertThat(delivery.getProcessedAt()).isNull();
    }

    @Test
    void markFailedRecordsTheFinalOutcome() {
        MessageDelivery delivery = newDelivery();

        delivery.markFailed(now.plusSeconds(60), "gave up");

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getProcessedAt()).isEqualTo(now.plusSeconds(60));
        assertThat(delivery.getLastError()).isEqualTo("gave up");
    }

    @Test
    void releasingAClaimUndoesTheAttemptThatNeverHappened() {
        MessageDelivery delivery = newDelivery();
        delivery.markForRetry(now, "boom");

        delivery.releaseClaim();

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.getLockedAt()).isNull();
        assertThat(delivery.getAttempts()).isZero();
    }

    private MessageDelivery newDelivery() {
        return MessageDelivery.pending(UUID.randomUUID(), UUID.randomUUID(), "consumer-id", now);
    }
}
