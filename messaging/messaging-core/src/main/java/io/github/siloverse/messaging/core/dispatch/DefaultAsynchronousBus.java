package io.github.siloverse.messaging.core.dispatch;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import io.github.siloverse.messaging.core.api.AsynchronousBus;
import io.github.siloverse.messaging.core.api.Command;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;

public class DefaultAsynchronousBus implements AsynchronousBus {

    private final MessageNameRegistry messageNameRegistry;
    private final MessageTransport messageTransport;
    private final PayloadSerializer payloadSerializer;

    public DefaultAsynchronousBus(
            MessageNameRegistry messageNameRegistry,
            MessageTransport messageTransport,
            PayloadSerializer payloadSerializer
    ) {
        Objects.requireNonNull(messageNameRegistry, "messageNameRegistry must not be null");
        Objects.requireNonNull(messageTransport, "messageTransport must not be null");
        Objects.requireNonNull(payloadSerializer, "payloadSerializer must not be null");

        this.messageNameRegistry = messageNameRegistry;
        this.messageTransport = messageTransport;
        this.payloadSerializer = payloadSerializer;
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

        var envelope = new Envelope(
                UUID.randomUUID(),
                messageNameRegistry.nameOf(message.getClass()),
                Map.of("content-type", payloadSerializer.contentType()),
                payloadSerializer.serialize(message)
        );
        messageTransport.send(envelope);
    }
}
