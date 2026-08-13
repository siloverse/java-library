package io.github.siloverse.messaging.core.transport;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Envelope(
        UUID messageId,
        String messageType,
        Map<String, String> headers,
        byte[] payload
) {
    public Envelope {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(messageType, "messageType must not be null");
        headers = Map.copyOf(headers);
        payload = payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
