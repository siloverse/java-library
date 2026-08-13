package io.github.siloverse.messaging.core.dispatch;

import java.util.Objects;

import io.github.siloverse.messaging.core.api.AsynchronousBus;
import io.github.siloverse.messaging.core.api.Command;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.EnvelopeFactory;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;

public class DefaultAsynchronousBus implements AsynchronousBus {

    private final EnvelopeFactory envelopeFactory;
    private final MessageTransport messageTransport;

    public DefaultAsynchronousBus(
            MessageNameRegistry messageNameRegistry,
            MessageTransport messageTransport,
            PayloadSerializer payloadSerializer
    ) {
        Objects.requireNonNull(messageTransport, "messageTransport must not be null");

        this.envelopeFactory = new EnvelopeFactory(messageNameRegistry, payloadSerializer);
        this.messageTransport = messageTransport;
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
        messageTransport.send(envelopeFactory.envelopeFor(message));
    }
}
