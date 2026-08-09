package io.github.siloverse.messaging.api;

/**
 * Publishes an event to every registered consumer.
 *
 * <p>Which implementation is injected depends on {@code messaging.event.mode}:
 * synchronous, durable asynchronous, or transaction aware durable asynchronous.
 */
public interface EventBus {

    /**
     * Evaluates the provider and delivers the resulting event to all of its consumers.
     *
     * @param provider supplies the event; evaluated on the calling thread
     */
    void publish(MessageProvider<? extends Event> provider);
}
