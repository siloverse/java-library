package io.github.siloverse.messaging.async;

import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.persistence.DurableMessageStore;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Stores events durably and returns immediately; the poller delivers them later.
 *
 * <p>Runs with {@code REQUIRED} propagation. Callers that need the message to commit together with
 * their business changes should use
 * {@link io.github.siloverse.messaging.transaction.TransactionAwareAsynchronousEventBus} instead,
 * which refuses to run without a caller transaction.
 */
public class AsynchronousEventBus implements EventBus {

    private final DurableMessageStore store;

    public AsynchronousEventBus(DurableMessageStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void publish(MessageProvider<? extends Event> provider) {
        Event event = Objects.requireNonNull(provider, "provider must not be null").provide();
        Objects.requireNonNull(event, "MessageProvider must not provide a null event");
        store.storeEvent(event);
    }
}
