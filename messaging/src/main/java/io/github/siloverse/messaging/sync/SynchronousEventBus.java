package io.github.siloverse.messaging.sync;

import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.dispatch.EventDispatcher;

import java.util.Objects;

/**
 * Publishes events immediately, on the caller's thread and inside the caller's transaction.
 *
 * <p>Nothing is persisted and consumer failures propagate to the caller.
 */
public class SynchronousEventBus implements EventBus {

    private final EventDispatcher dispatcher;

    public SynchronousEventBus(EventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void publish(MessageProvider<? extends Event> provider) {
        Event event = Objects.requireNonNull(provider, "provider must not be null").provide();
        Objects.requireNonNull(event, "MessageProvider must not provide a null event");
        dispatcher.dispatch(event);
    }
}
