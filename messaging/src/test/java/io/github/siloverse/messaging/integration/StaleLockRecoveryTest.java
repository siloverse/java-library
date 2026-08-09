package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Crash recovery: a delivery whose worker never came back must not stay stuck.
 *
 * <p>A JVM crash is simulated by putting a delivery into {@code PROCESSING} with a
 * {@code locked_at} older than the configured lock timeout, which is exactly the state a crash
 * leaves behind.
 */
class StaleLockRecoveryTest extends AbstractDurableMessagingTest {

    @Test
    void anExpiredLockMakesTheDeliveryClaimableAgain() {
        UUID orderId = UUID.randomUUID();
        orderService.sendInTransaction(new ConfirmOrder(orderId));
        UUID deliveryId = deliveriesOf(singleStoredMessage()).getFirst().getId();
        simulateCrashWhileProcessing(deliveryId, 1, Duration.ofMinutes(10));

        poller.recoverAbandoned();

        assertThat(deliveries.findById(deliveryId)).get().satisfies(recovered -> {
            assertThat(recovered.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(recovered.getLockedAt()).isNull();
            assertThat(recovered.getLastError()).contains("lock expired");
        });

        makeAvailableNow(deliveryId);
        assertThat(pollAndAwaitCompletion()).isEqualTo(1);
        assertThat(consumers.commands()).containsExactly(new ConfirmOrder(orderId));
    }

    @Test
    void aFreshLockIsLeftAlone() {
        orderService.sendInTransaction(new ConfirmOrder(UUID.randomUUID()));
        UUID deliveryId = deliveriesOf(singleStoredMessage()).getFirst().getId();
        simulateCrashWhileProcessing(deliveryId, 1, Duration.ofSeconds(5));

        assertThat(poller.recoverAbandoned()).isZero();

        assertThat(deliveries.findById(deliveryId)).get()
                .satisfies(untouched -> assertThat(untouched.getStatus()).isEqualTo(DeliveryStatus.PROCESSING));
    }

    @Test
    void anAbandonedDeliveryWithoutAttemptsLeftIsFailed() {
        orderService.sendInTransaction(new ConfirmOrder(UUID.randomUUID()));
        UUID deliveryId = deliveriesOf(singleStoredMessage()).getFirst().getId();
        simulateCrashWhileProcessing(deliveryId, 3, Duration.ofMinutes(10));

        assertThat(poller.recoverAbandoned()).isEqualTo(1);

        assertThat(deliveries.findById(deliveryId)).get().satisfies(failed -> {
            assertThat(failed.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(failed.getProcessedAt()).isNotNull();
            assertThat(failed.getLastError()).contains("out of attempts");
        });
    }

    @Test
    void recoveryRunsAsPartOfANormalPollCycle() {
        UUID orderId = UUID.randomUUID();
        orderService.sendInTransaction(new ConfirmOrder(orderId));
        UUID deliveryId = deliveriesOf(singleStoredMessage()).getFirst().getId();
        simulateCrashWhileProcessing(deliveryId, 1, Duration.ofMinutes(10));

        poller.pollOnce();

        assertThat(deliveries.findById(deliveryId)).get()
                .satisfies(recovered -> assertThat(recovered.getStatus()).isEqualTo(DeliveryStatus.PENDING));
    }

    private void simulateCrashWhileProcessing(UUID deliveryId, int attempts, Duration lockedAgo) {
        jdbc.update("""
                        UPDATE message_deliveries
                           SET status = 'PROCESSING', locked_at = ?, attempts = ?
                         WHERE id = ?
                        """,
                Instant.now().minus(lockedAgo).atOffset(ZoneOffset.UTC), attempts, deliveryId);
    }
}
