package io.github.siloverse.messaging.core.transport;

public interface PayloadDeserializer {
    <T> T deserialize(byte[] payload, Class<T> type);
}
