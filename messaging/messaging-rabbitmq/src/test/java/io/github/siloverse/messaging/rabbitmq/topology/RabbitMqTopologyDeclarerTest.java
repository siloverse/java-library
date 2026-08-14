package io.github.siloverse.messaging.rabbitmq.topology;

import com.rabbitmq.client.Connection;
import io.github.siloverse.messaging.core.api.Command;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnectionSettings;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnector;
import io.github.siloverse.messaging.rabbitmq.transport.RabbitMqMessageTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RabbitMqTopologyDeclarerTest {

    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static Connection connection;

    @BeforeAll
    static void startBrokerAndConnect() {
        rabbit.start();
        connection = RabbitMqConnector.connect(new RabbitMqConnectionSettings(
                rabbit.getHost(), rabbit.getAmqpPort(), rabbit.getAdminUsername(), rabbit.getAdminPassword()));
    }

    @AfterAll
    static void closeConnection() throws Exception {
        connection.close();
    }

    @Test
    void consumerQueueReceivesWhatTheTransportPublishes() throws Exception {
        var consumers = new ConsumerRegistry();
        consumers.register(definition("order-worker", OrderConfirmed.class, "onOrderConfirmed"));
        consumers.freeze();

        var declarer = new RabbitMqTopologyDeclarer(connection);
        declarer.declareConsumerTopology("billing-silo", consumers, names());

        new RabbitMqMessageTransport(connection).send(envelope("order-silo.order-confirmed"));

        try (var check = connection.createChannel()) {
            var delivered = check.basicGet("billing-silo-order-worker", true);
            assertThat(delivered)
                    .as("queue <service>-<consumer-id> must exist and be bound to the message type's exchange")
                    .isNotNull();
        }
    }

    @Test
    void zeroConsumerEventIsSafeToPublishAfterPublisherDeclaration() {
        var declarer = new RabbitMqTopologyDeclarer(connection);
        declarer.declarePublisherTopology(names());

        // nobody consumes invoice-requested: the exchange must exist so publishing is a
        // clean no-op (acked, dropped) instead of a 404 channel error
        assertThatCode(() -> new RabbitMqMessageTransport(connection).send(envelope("billing-silo.invoice-requested")))
                .doesNotThrowAnyException();
    }

    @Test
    void consumedClassMissingFromNameRegistryFailsStartup() {
        var consumers = new ConsumerRegistry();
        consumers.register(definition("order-worker-unnamed", OrderConfirmed.class, "onOrderConfirmed"));
        consumers.freeze();

        var namesWithoutOrderConfirmed = MessageNameRegistry.builder()
                .register(ConfirmOrder.class, "billing-silo.confirm-order")
                .freeze();

        var declarer = new RabbitMqTopologyDeclarer(connection);
        assertThatThrownBy(() -> declarer.declareConsumerTopology(
                "billing-silo", consumers, namesWithoutOrderConfirmed))
                .as("a consumed class with no wire name is a startup binding failure, not a"
                        + " first-publish surprise")
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(OrderConfirmed.class.getName());
    }

    @Test
    void declarationsAreIdempotentAcrossRestartsAndBothSides() {
        var consumers = new ConsumerRegistry();
        consumers.register(definition("order-worker-idem", OrderConfirmed.class, "onOrderConfirmed"));
        consumers.register(definition("confirm-order-idem", ConfirmOrder.class, "onConfirmOrder"));
        consumers.freeze();

        var declarer = new RabbitMqTopologyDeclarer(connection);

        // restarts re-declare, and publisher + consumer declare the same exchanges
        assertThatCode(() -> {
            declarer.declarePublisherTopology(names());
            declarer.declarePublisherTopology(names());
            declarer.declareConsumerTopology("billing-silo", consumers, names());
            declarer.declareConsumerTopology("billing-silo", consumers, names());
        }).doesNotThrowAnyException();
    }

    @Test
    void exchangeAlreadyDeclaredWithDifferentTypeIsAConfigurationError() throws Exception {
        // someone else declared this wire name as a TOPIC exchange: a topology conflict
        try (var sabotage = connection.createChannel()) {
            sabotage.exchangeDeclare("audit-silo.entry-recorded", "topic", true);
        }

        var conflictingNames = MessageNameRegistry.builder()
                .register(EntryRecorded.class, "audit-silo.entry-recorded")
                .freeze();

        var declarer = new RabbitMqTopologyDeclarer(connection);
        assertThatThrownBy(() -> declarer.declarePublisherTopology(conflictingNames))
                .as("406 precondition-failed = the topology is misconfigured, not the environment")
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("audit-silo.entry-recorded");
    }

    // -- fixtures ------------------------------------------------------------

    public static final class OrderConfirmed implements Event {
    }

    public static final class InvoiceRequested implements Event {
    }

    public static final class ConfirmOrder implements Command {
    }

    public static final class EntryRecorded implements Event {
    }

    static final class OrderWorker {
        void onOrderConfirmed(OrderConfirmed event) {
        }

        void onConfirmOrder(ConfirmOrder command) {
        }
    }

    private static MessageNameRegistry names() {
        return MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .register(ConfirmOrder.class, "billing-silo.confirm-order")
                .register(InvoiceRequested.class, "billing-silo.invoice-requested")
                .freeze();
    }

    private static ConsumerDefinition definition(String id, Class<?> messageClass, String methodName) {
        try {
            return new ConsumerDefinition(
                    id, messageClass, new OrderWorker(),
                    OrderWorker.class.getDeclaredMethod(methodName, messageClass), -1);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("fixture consumer method is missing", e);
        }
    }

    private static Envelope envelope(String messageType) {
        return new Envelope(
                UUID.randomUUID(),
                messageType,
                Map.of("content-type", "application/json"),
                "{\"orderId\":42}".getBytes(StandardCharsets.UTF_8));
    }
}
