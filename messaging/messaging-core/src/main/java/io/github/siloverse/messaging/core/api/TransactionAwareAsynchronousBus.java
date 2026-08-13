package io.github.siloverse.messaging.core.api;

public interface TransactionAwareAsynchronousBus {
    void publish(Event event);
    void send(Command command);
}
