package io.github.siloverse.messaging.api;

/**
 * Sends a command to its single consumer.
 *
 * <p>Which implementation is injected depends on {@code messaging.command.mode}:
 * synchronous, durable asynchronous, or transaction aware durable asynchronous.
 */
public interface CommandBus {

    /**
     * Evaluates the provider and delivers the resulting command to exactly one consumer.
     *
     * @param provider supplies the command; evaluated on the calling thread
     */
    void send(MessageProvider<? extends Command> provider);
}
