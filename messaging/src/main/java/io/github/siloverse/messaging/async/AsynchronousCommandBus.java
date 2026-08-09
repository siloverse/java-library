package io.github.siloverse.messaging.async;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.persistence.DurableMessageStore;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Stores commands durably and returns immediately; the poller delivers them later.
 *
 * <p>Runs with {@code REQUIRED} propagation, so it joins an ongoing transaction when there is one
 * and opens a short transaction of its own when there is not. Callers that need the message to
 * commit together with their business changes should use
 * {@link io.github.siloverse.messaging.transaction.TransactionAwareAsynchronousCommandBus}
 * instead, which refuses to run without a caller transaction.
 */
public class AsynchronousCommandBus implements CommandBus {

    private final DurableMessageStore store;

    public AsynchronousCommandBus(DurableMessageStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void send(MessageProvider<? extends Command> provider) {
        Command command = Objects.requireNonNull(provider, "provider must not be null").provide();
        Objects.requireNonNull(command, "MessageProvider must not provide a null command");
        store.storeCommand(command);
    }
}
