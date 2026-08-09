package io.github.siloverse.messaging.api;

/**
 * Supplies the message to be sent or published.
 *
 * <p>The provider is always evaluated on the calling thread, inside the {@code send()} or
 * {@code publish()} call. A provider is never retained and never invoked later from a worker
 * thread, so it is safe to close over request scoped or transaction scoped state.
 *
 * @param <M> the message type produced
 */
@FunctionalInterface
public interface MessageProvider<M extends Message> {

    /**
     * Produces the message. Called exactly once per {@code send()} / {@code publish()} invocation.
     *
     * @return the message, never {@code null}
     */
    M provide();

    /**
     * Wraps an already constructed message.
     *
     * @param message the message to provide
     * @param <M>     the message type
     * @return a provider returning {@code message}
     */
    static <M extends Message> MessageProvider<M> of(M message) {
        return () -> message;
    }
}
