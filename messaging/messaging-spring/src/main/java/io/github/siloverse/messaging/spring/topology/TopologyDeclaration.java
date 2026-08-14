package io.github.siloverse.messaging.spring.topology;

/**
 * Broker-agnostic hook for declaring topology at startup.
 *
 * <p>The application provides one bean of this type wrapping its broker adapter's declarer
 * (e.g. {@code RabbitMqTopologyDeclarer}); the messaging lifecycle invokes every such bean
 * AFTER the consumer registry is frozen and BEFORE the outbox relay starts, so exchanges,
 * queues and bindings exist before the first message can possibly be published. Failures
 * propagate and abort startup -- broken topology is a boot problem, never a runtime surprise.
 */
@FunctionalInterface
public interface TopologyDeclaration {

    void declare();
}
