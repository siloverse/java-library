package io.github.siloverse.messaging.core.dispatch;

import java.util.Objects;

import io.github.siloverse.messaging.core.api.Command;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.api.TransactionAwareAsynchronousBus;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.EnvelopeFactory;
import io.github.siloverse.messaging.core.transport.OutboxWriter;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;

/**
 * Publishes by appending to the outbox inside the caller's transaction.
 *
 * <p>Delivery contract: a message published in a transaction that COMMITS is delivered
 * at least once -- the relay republishes ambiguous rows after a crash, so duplicates are
 * possible (identified by {@link Envelope#messageId()}) and consumers must be idempotent.
 * A transaction that ROLLS BACK delivers nothing: the outbox row rolls back with the
 * business data.
 *
 * <p>This bus never manages transactions itself. The {@link OutboxWriter} implementation
 * inherits whatever transaction the calling thread is in; without one, the append is
 * effectively a direct write with no rollback safety.
 */
public class DefaultTransactionAwareAsynchronousBus implements TransactionAwareAsynchronousBus {

    private final EnvelopeFactory envelopeFactory;
    private final OutboxWriter outboxWriter;

    public DefaultTransactionAwareAsynchronousBus(
            MessageNameRegistry messageNameRegistry,
            OutboxWriter outboxWriter,
            PayloadSerializer payloadSerializer
    ) {
        Objects.requireNonNull(outboxWriter, "outboxWriter must not be null");

        this.envelopeFactory = new EnvelopeFactory(messageNameRegistry, payloadSerializer);
        this.outboxWriter = outboxWriter;
    }

    @Override
    public void publish(Event event) {
        dispatch(event);
    }

    @Override
    public void send(Command command) {
        dispatch(command);
    }

    private void dispatch(Object message) {
        outboxWriter.append(envelopeFactory.envelopeFor(message));
    }
}
