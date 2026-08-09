package io.github.siloverse.messaging.transaction;

import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.persistence.DurableMessageStore;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Durable event bus that writes the message inside the caller's transaction.
 *
 * <pre>{@code
 * @Transactional
 * public void confirm(UUID orderId) {
 *     order.confirm();                                    // UPDATE orders ...
 *     eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId)));  // INSERT messages ...
 * }                                                       // one COMMIT, or neither
 * }</pre>
 *
 * <p>{@code MANDATORY} propagation means publishing outside a transaction fails loudly. The insert
 * is part of the business transaction itself, not an {@code afterCommit} callback, so a crash can
 * never leave the business change committed without its event.
 */
public class TransactionAwareAsynchronousEventBus implements EventBus {

    private final DurableMessageStore store;

    public TransactionAwareAsynchronousEventBus(DurableMessageStore store) {
        this.store = store;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(MessageProvider<? extends Event> provider) {
        Event event = Objects.requireNonNull(provider, "provider must not be null").provide();
        Objects.requireNonNull(event, "MessageProvider must not provide a null event");
        store.storeEvent(event);
    }
}
