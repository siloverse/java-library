package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.api.MessageKind;
import io.github.siloverse.messaging.fixture.OrderCancelled;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import io.github.siloverse.messaging.persistence.entity.StoredMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durable asynchronous events: one message, one delivery per consumer.
 */
class DurableEventTest extends AbstractDurableMessagingTest {

    @Test
    void publishingStoresOneMessageAndOneDeliveryPerConsumer() {
        UUID orderId = UUID.randomUUID();

        orderService.publishInTransaction(new OrderConfirmed(orderId));

        StoredMessage stored = singleStoredMessage();
        assertThat(stored.getMessageKind()).isEqualTo(MessageKind.EVENT);

        List<MessageDelivery> created = deliveriesOf(stored);
        assertThat(created).hasSize(2);
        assertThat(created).extracting(MessageDelivery::getConsumerId).doesNotHaveDuplicates();
        assertThat(created).allSatisfy(delivery ->
                assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PENDING));
    }

    @Test
    void bothConsumersRunAndBothDeliveriesAreProcessed() {
        UUID orderId = UUID.randomUUID();
        orderService.publishInTransaction(new OrderConfirmed(orderId));

        assertThat(pollAndAwaitCompletion()).isEqualTo(2);

        assertThat(consumers.emails()).containsExactly(new OrderConfirmed(orderId));
        assertThat(consumers.analytics()).containsExactly(new OrderConfirmed(orderId));
        assertThat(deliveriesOf(singleStoredMessage()))
                .allSatisfy(delivery ->
                        assertThat(reload(delivery).getStatus()).isEqualTo(DeliveryStatus.PROCESSED));
    }

    @Test
    void anEventWithoutConsumersIsStoredWithNoDeliveries() {
        orderService.publishInTransaction(new OrderCancelled(UUID.randomUUID()));

        StoredMessage stored = singleStoredMessage();
        assertThat(stored.getMessageType()).isEqualTo(OrderCancelled.class.getName());
        assertThat(deliveriesOf(stored)).isEmpty();
        assertThat(poller.pollOnce()).isZero();
    }

    @Test
    void severalEventsProduceSeparateMessages() {
        orderService.publishInTransaction(new OrderConfirmed(UUID.randomUUID()));
        orderService.publishInTransaction(new OrderConfirmed(UUID.randomUUID()));

        assertThat(messages.count()).isEqualTo(2);
        assertThat(deliveries.count()).isEqualTo(4);
    }
}
