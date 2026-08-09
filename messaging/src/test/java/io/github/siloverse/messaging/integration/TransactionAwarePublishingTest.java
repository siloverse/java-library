package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The point of the transaction aware buses: business state and message share one transaction.
 */
class TransactionAwarePublishingTest extends AbstractDurableMessagingTest {

    @Test
    void commitPersistsBothBusinessStateAndMessage() {
        UUID orderId = UUID.randomUUID();

        orderService.confirm(orderId);

        assertThat(orders.findById(orderId)).get()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo("CONFIRMED"));
        assertThat(messages.count()).isEqualTo(1);
        assertThat(deliveries.count()).isEqualTo(2);
    }

    @Test
    void rollbackPersistsNeitherBusinessStateNorMessage() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> orderService.confirmThenFail(orderId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("business logic failed");

        assertThat(orders.findById(orderId)).isEmpty();
        assertThat(messages.count()).isZero();
        assertThat(deliveries.count()).isZero();
    }

    @Test
    void publishingOutsideATransactionIsRejected() {
        assertThatThrownBy(() -> orderService.publishWithoutTransaction(new OrderConfirmed(UUID.randomUUID())))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(messages.count()).isZero();
    }

    @Test
    void aRolledBackMessageIsNeverDelivered() {
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> orderService.confirmThenFail(orderId)).isInstanceOf(IllegalStateException.class);

        assertThat(poller.pollOnce()).isZero();
        assertThat(consumers.emails()).isEmpty();
        assertThat(consumers.analytics()).isEmpty();
    }

    @Test
    void aCommittedCommandSurvivesAndIsDelivered() {
        UUID orderId = UUID.randomUUID();

        orderService.sendInTransaction(new ConfirmOrder(orderId));
        pollAndAwaitCompletion();

        assertThat(consumers.commands()).containsExactly(new ConfirmOrder(orderId));
    }
}
