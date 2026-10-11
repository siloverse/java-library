package io.github.siloverse.messaging.core.transport;

import io.github.siloverse.messaging.core.naming.MessageNameRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Builds the wire envelope for a message: mints a fresh messageId, resolves the wire name, serializes the payload, and
 * stamps the content-type header.
 *
 * <p>The single definition of envelope construction -- both buses delegate here so the two
 * wires (direct transport and outbox) can never drift apart.
 */
public final class EnvelopeFactory {

    private final MessageNameRegistry messageNameRegistry;
    private final EnvelopeHeadersContributor envelopeHeadersContributor;
    private final PayloadSerializer payloadSerializer;

    public EnvelopeFactory(MessageNameRegistry messageNameRegistry,
            EnvelopeHeadersContributor envelopeHeadersContributor, PayloadSerializer payloadSerializer) {
        Objects.requireNonNull(messageNameRegistry, "messageNameRegistry must not be null");
        Objects.requireNonNull(envelopeHeadersContributor, "envelopeHeadersContributor must not be null");
        Objects.requireNonNull(payloadSerializer, "payloadSerializer must not be null");

        this.messageNameRegistry = messageNameRegistry;
        this.envelopeHeadersContributor = envelopeHeadersContributor;
        this.payloadSerializer = payloadSerializer;
    }

    public Envelope envelopeFor(Object message) {
        return new Envelope(UUID.randomUUID(), messageNameRegistry.nameOf(message.getClass()), getEvelopHeadersMap(),
                payloadSerializer.serialize(message));
    }

    private Map<String, String> getEvelopHeadersMap() {
        var headers = new HashMap<>(Map.of("content-type", payloadSerializer.contentType()));
        envelopeHeadersContributor.contribute(headers);
        return headers;
    }
}
