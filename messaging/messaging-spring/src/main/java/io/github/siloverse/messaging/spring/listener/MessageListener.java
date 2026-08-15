package io.github.siloverse.messaging.spring.listener;

/**
 * Broker-agnostic hook for the consuming side's lifecycle.
 *
 * <p>The application provides beans of this type wrapping its broker adapter's listener
 * (e.g. {@code RabbitMqMessageListener}, typically together with the separate consume
 * connection the assembly opens on {@code start()} and closes on {@code stop()}). The
 * messaging lifecycle starts every such bean AFTER topology declaration -- queues must exist
 * before anyone consumes them -- and stops them FIRST on shutdown, before the relay and
 * before any connection beans are destroyed.
 */
public interface MessageListener {

    void start();

    void stop();
}
