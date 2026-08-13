package io.github.siloverse.messaging.core.transport;

public interface MessageTransport {
    void send(Envelope envelope);
}
