package io.github.siloverse.messaging.core.api;

public interface AsynchronousBus {
    void publish(Event event);
    void send(Command command);
}
