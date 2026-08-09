package io.github.siloverse.messaging.transaction;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.persistence.DurableMessageStore;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Durable command bus that writes the message inside the caller's transaction.
 *
 * <p>{@code MANDATORY} propagation means the caller must already be transactional; sending outside
 * a transaction fails loudly rather than silently losing the coupling between business state and
 * message. The insert is part of the business transaction itself, not an {@code afterCommit}
 * callback, so there is no window in which the business change is committed but the command is not.
 */
public class TransactionAwareAsynchronousCommandBus implements CommandBus {

    private final DurableMessageStore store;

    public TransactionAwareAsynchronousCommandBus(DurableMessageStore store) {
        this.store = store;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void send(MessageProvider<? extends Command> provider) {
        Command command = Objects.requireNonNull(provider, "provider must not be null").provide();
        Objects.requireNonNull(command, "MessageProvider must not provide a null command");
        store.storeCommand(command);
    }
}
