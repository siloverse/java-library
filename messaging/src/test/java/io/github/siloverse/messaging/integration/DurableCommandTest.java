package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.api.MessageKind;
import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import io.github.siloverse.messaging.persistence.entity.StoredMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durable asynchronous commands: one message, one delivery, one consumer.
 */
class DurableCommandTest extends AbstractDurableMessagingTest {

    @Test
    void sendingStoresTheMessageAndExactlyOneDelivery() {
        UUID orderId = UUID.randomUUID();

        orderService.sendInTransaction(new ConfirmOrder(orderId));

        StoredMessage stored = singleStoredMessage();
        assertThat(stored.getMessageKind()).isEqualTo(MessageKind.COMMAND);
        assertThat(stored.getMessageType()).isEqualTo(ConfirmOrder.class.getName());
        assertThat(stored.getPayload()).contains(orderId.toString());

        List<MessageDelivery> created = deliveriesOf(stored);
        assertThat(created).singleElement().satisfies(delivery -> {
            assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(delivery.getAttempts()).isZero();
            assertThat(delivery.getProcessedAt()).isNull();
            assertThat(delivery.getLockedAt()).isNull();
        });
    }

    @Test
    void sendingDoesNotInvokeTheConsumer() {
        orderService.sendInTransaction(new ConfirmOrder(UUID.randomUUID()));

        assertThat(consumers.commands()).isEmpty();
    }

    @Test
    void pollingDeliversTheCommandOnceAndMarksItProcessed() {
        UUID orderId = UUID.randomUUID();
        orderService.sendInTransaction(new ConfirmOrder(orderId));

        assertThat(pollAndAwaitCompletion()).isEqualTo(1);

        assertThat(consumers.commands()).containsExactly(new ConfirmOrder(orderId));
        MessageDelivery delivery = deliveriesOf(singleStoredMessage()).getFirst();
        assertThat(reload(delivery)).satisfies(processed -> {
            assertThat(processed.getStatus()).isEqualTo(DeliveryStatus.PROCESSED);
            assertThat(processed.getAttempts()).isEqualTo(1);
            assertThat(processed.getProcessedAt()).isNotNull();
            assertThat(processed.getLockedAt()).isNull();
            assertThat(processed.getLastError()).isNull();
        });
    }

    @Test
    void aProcessedDeliveryIsNeverClaimedAgain() {
        orderService.sendInTransaction(new ConfirmOrder(UUID.randomUUID()));
        pollAndAwaitCompletion();

        assertThat(poller.pollOnce()).isZero();
        assertThat(consumers.commands()).hasSize(1);
    }
}
