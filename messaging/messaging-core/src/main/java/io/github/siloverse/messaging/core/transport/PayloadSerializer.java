package io.github.siloverse.messaging.core.transport;

public interface PayloadSerializer {
    byte[] serialize(Object message);

    String contentType();
}
