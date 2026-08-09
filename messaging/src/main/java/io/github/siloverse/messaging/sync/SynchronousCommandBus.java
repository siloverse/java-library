package io.github.siloverse.messaging.sync;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.dispatch.CommandDispatcher;

import java.util.Objects;

/**
 * Dispatches commands immediately, on the caller's thread and inside the caller's transaction.
 *
 * <p>Nothing is persisted and consumer failures propagate to the caller.
 */
public class SynchronousCommandBus implements CommandBus {

    private final CommandDispatcher dispatcher;

    public SynchronousCommandBus(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void send(MessageProvider<? extends Command> provider) {
        Command command = Objects.requireNonNull(provider, "provider must not be null").provide();
        Objects.requireNonNull(command, "MessageProvider must not provide a null command");
        dispatcher.dispatch(command);
    }
}
