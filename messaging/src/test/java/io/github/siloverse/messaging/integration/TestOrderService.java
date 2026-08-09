package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Application service that changes business state and publishes in the same transaction.
 */
public class TestOrderService {

    private final TestOrderRepository orders;
    private final EventBus eventBus;
    private final CommandBus commandBus;

    public TestOrderService(TestOrderRepository orders, EventBus eventBus, CommandBus commandBus) {
        this.orders = orders;
        this.eventBus = eventBus;
        this.commandBus = commandBus;
    }

    /**
     * The shape the library is built for: business change and event in one transaction.
     *
     * @param orderId the order to confirm
     */
    @Transactional
    public void confirm(UUID orderId) {
        orders.save(new TestOrder(orderId, "CONFIRMED"));
        eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId)));
    }

    /**
     * Same as {@link #confirm} but the business logic fails afterwards, so nothing may survive.
     *
     * @param orderId the order to confirm
     */
    @Transactional
    public void confirmThenFail(UUID orderId) {
        orders.save(new TestOrder(orderId, "CONFIRMED"));
        eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId)));
        throw new IllegalStateException("business logic failed after publishing");
    }

    @Transactional
    public void publishInTransaction(Event event) {
        eventBus.publish(MessageProvider.of(event));
    }

    @Transactional
    public void sendInTransaction(Command command) {
        commandBus.send(MessageProvider.of(command));
    }

    public void publishWithoutTransaction(Event event) {
        eventBus.publish(MessageProvider.of(event));
    }
}
