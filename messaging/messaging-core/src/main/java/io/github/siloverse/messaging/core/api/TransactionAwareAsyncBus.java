package io.github.siloverse.messaging.core.api;

public interface TransactionAwareAsyncBus {
    void publish(Event event);
    void send(Command command);
}
