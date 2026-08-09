package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failing consumers: attempts go up, {@code available_at} moves forward, and after the configured
 * number of attempts the delivery is given up on. {@code messaging.async.max-attempts} is 3 and
 * {@code messaging.async.retry-delay} is 30 seconds for these tests.
 */
class RetryTest extends AbstractDurableMessagingTest {

    @Test
    void aFailingConsumerIncrementsAttemptsAndSchedulesARetry() {
        UUID orderId = UUID.randomUUID();
        consumers.failCommandFor(orderId);
        orderService.sendInTransaction(new ConfirmOrder(orderId));
        Instant beforePolling = Instant.now();

        pollAndAwaitCompletion();

        MessageDelivery delivery = deliveriesOf(singleStoredMessage()).getFirst();
        assertThat(reload(delivery)).satisfies(retried -> {
            assertThat(retried.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(retried.getAttempts()).isEqualTo(1);
            assertThat(retried.getAvailableAt()).isAfter(beforePolling.plusSeconds(20));
            assertThat(retried.getLockedAt()).isNull();
            assertThat(retried.getLastError()).contains("confirm was told to fail");
        });
    }

    @Test
    void aDeliveryIsNotClaimedBeforeItsRetryDelayHasPassed() {
        UUID orderId = UUID.randomUUID();
        consumers.failCommandFor(orderId);
        orderService.sendInTransaction(new ConfirmOrder(orderId));

        pollAndAwaitCompletion();

        assertThat(poller.pollOnce()).isZero();
        assertThat(consumers.commands()).hasSize(1);
    }

    @Test
    void aDeliveryIsMarkedFailedAfterTheConfiguredNumberOfAttempts() {
        UUID orderId = UUID.randomUUID();
        consumers.failCommandFor(orderId);
        orderService.sendInTransaction(new ConfirmOrder(orderId));
        UUID deliveryId = deliveriesOf(singleStoredMessage()).getFirst().getId();

        for (int attempt = 1; attempt <= 3; attempt++) {
            makeAvailableNow(deliveryId);
            pollAndAwaitCompletion();
        }

        assertThat(consumers.commands()).hasSize(3);
        assertThat(deliveries.findById(deliveryId)).get().satisfies(failed -> {
            assertThat(failed.getStatus()).isEqualTo(DeliveryStatus.FAILED);
            assertThat(failed.getAttempts()).isEqualTo(3);
            assertThat(failed.getProcessedAt()).isNotNull();
            assertThat(failed.getLastError()).contains("confirm was told to fail");
        });
    }

    @Test
    void aFailedDeliveryIsNeverClaimedAgain() {
        UUID orderId = UUID.randomUUID();
        consumers.failCommandFor(orderId);
        orderService.sendInTransaction(new ConfirmOrder(orderId));
        UUID deliveryId = deliveriesOf(singleStoredMessage()).getFirst().getId();

        for (int attempt = 1; attempt <= 3; attempt++) {
            makeAvailableNow(deliveryId);
            pollAndAwaitCompletion();
        }
        makeAvailableNow(deliveryId);

        assertThat(poller.pollOnce()).isZero();
        assertThat(consumers.commands()).hasSize(3);
    }

    @Test
    void aRecoveredConsumerSucceedsOnALaterAttempt() {
        UUID orderId = UUID.randomUUID();
        consumers.failCommandFor(orderId);
        orderService.sendInTransaction(new ConfirmOrder(orderId));
        UUID deliveryId = deliveriesOf(singleStoredMessage()).getFirst().getId();

        pollAndAwaitCompletion();
        consumers.failCommandFor(null);
        makeAvailableNow(deliveryId);
        pollAndAwaitCompletion();

        assertThat(deliveries.findById(deliveryId)).get().satisfies(processed -> {
            assertThat(processed.getStatus()).isEqualTo(DeliveryStatus.PROCESSED);
            assertThat(processed.getAttempts()).isEqualTo(2);
            assertThat(processed.getLastError()).isNull();
        });
    }
}
