package io.github.siloverse.messaging.rabbitmq.listener;

import com.rabbitmq.client.Connection;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.consumer.Consumer;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.dispatch.MessageDispatcher;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.PayloadDeserializer;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnectionSettings;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnector;
import io.github.siloverse.messaging.rabbitmq.topology.RabbitMqTopologyDeclarer;
import io.github.siloverse.messaging.rabbitmq.transport.RabbitMqMessageTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * The consuming side's executable spec: dispatch typed events to @Consumer methods, ack on
 * success, one immediate retry on first failure, park in the DLQ on the second.
 */
class RabbitMqMessageListenerTest {

    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static Connection publishConnection;
    static Connection consumeConnection;

    @BeforeAll
    static void startBrokerAndConnect() {
        rabbit.start();
        var settings = new RabbitMqConnectionSettings(
                rabbit.getHost(), rabbit.getAmqpPort(), rabbit.getAdminUsername(), rabbit.getAdminPassword());
        publishConnection = RabbitMqConnector.connect(settings);
        consumeConnection = RabbitMqConnector.connect(settings);
    }

    @AfterAll
    static void closeConnections() throws Exception {
        consumeConnection.close();
        publishConnection.close();
    }

    @Test
    void deliversTypedEventToTheConsumerAndAcks() throws Exception {
        var worker = new RecordingWorker();
        var consumers = registryFor("order-recorder", OrderConfirmed.class, worker, "onOrderConfirmed");
        new RabbitMqTopologyDeclarer(publishConnection).declareConsumerTopology("billing-silo", consumers, names());

        var listener = new RabbitMqMessageListener(
                consumeConnection, "billing-silo", consumers, names(), new IntPayloadDeserializer(), new MessageDispatcher());
        listener.start();
        try {
            new RabbitMqMessageTransport(publishConnection).send(envelope("order-silo.order-confirmed", "42"));

            var received = worker.received.poll(10, TimeUnit.SECONDS);
            assertThat(received)
                    .as("the @Consumer method must receive the DESERIALIZED, TYPED event")
                    .isEqualTo(new OrderConfirmed(42));

            Thread.sleep(200); // let the ack land
            try (var check = publishConnection.createChannel()) {
                assertThat(check.basicGet("billing-silo-order-recorder", true))
                        .as("success must ACK: the message is consumed, not just observed")
                        .isNull();
            }
        } finally {
            listener.stop();
        }
    }

    @Test
    void firstFailureGetsOneImmediateRetry() throws Exception {
        var worker = new FlakyWorker();
        var consumers = registryFor("order-flaky", PaymentRecorded.class, worker, "onPaymentRecorded");
        new RabbitMqTopologyDeclarer(publishConnection).declareConsumerTopology("billing-silo", consumers, names());

        var listener = new RabbitMqMessageListener(
                consumeConnection, "billing-silo", consumers, names(), new IntPayloadDeserializer(), new MessageDispatcher());
        listener.start();
        try {
            new RabbitMqMessageTransport(publishConnection).send(envelope("order-silo.payment-recorded", "7"));

            var received = worker.received.poll(10, TimeUnit.SECONDS);
            assertThat(received)
                    .as("a transient failure must be retried once and then succeed")
                    .isEqualTo(new PaymentRecorded(7));
            assertThat(worker.attempts.get())
                    .as("exactly two invocations: the failure and the successful redelivery")
                    .isEqualTo(2);
        } finally {
            listener.stop();
        }
    }

    @Test
    void poisonMessageParksInTheDlqAfterExactlyTwoAttempts() throws Exception {
        var worker = new PoisonWorker();
        var consumers = registryFor("order-poison", EntryRecorded.class, worker, "onEntryRecorded");
        new RabbitMqTopologyDeclarer(publishConnection).declareConsumerTopology("billing-silo", consumers, names());

        var listener = new RabbitMqMessageListener(
                consumeConnection, "billing-silo", consumers, names(), new IntPayloadDeserializer(), new MessageDispatcher());
        listener.start();
        try {
            new RabbitMqMessageTransport(publishConnection).send(envelope("order-silo.entry-recorded", "13"));

            var parked = awaitParked("billing-silo-order-poison.dlq", Duration.ofSeconds(10));
            assertThat(parked.getProps().getHeaders())
                    .as("the broker's dead-letter audit trail must ride along")
                    .containsKey("x-death");
            assertThat(worker.attempts.get())
                    .as("a deterministic failure costs exactly two invocations -- never a loop")
                    .isEqualTo(2);
        } finally {
            listener.stop();
        }
    }

    // -- fixtures ------------------------------------------------------------

    public record OrderConfirmed(int orderId) implements Event {
    }

    public record PaymentRecorded(int paymentId) implements Event {
    }

    public record EntryRecorded(int entryId) implements Event {
    }

    static final class RecordingWorker {
        final BlockingQueue<OrderConfirmed> received = new LinkedBlockingQueue<>();

        @Consumer(id = "order-recorder")
        void onOrderConfirmed(OrderConfirmed event) {
            received.add(event);
        }
    }

    static final class FlakyWorker {
        final AtomicInteger attempts = new AtomicInteger();
        final BlockingQueue<PaymentRecorded> received = new LinkedBlockingQueue<>();

        @Consumer(id = "order-flaky")
        void onPaymentRecorded(PaymentRecorded event) {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("transient failure, works on retry");
            }
            received.add(event);
        }
    }

    static final class PoisonWorker {
        final AtomicInteger attempts = new AtomicInteger();

        @Consumer(id = "order-poison")
        void onEntryRecorded(EntryRecorded event) {
            attempts.incrementAndGet();
            throw new IllegalStateException("deterministic failure, poison message");
        }
    }

    /** Payload is the int rendered as a UTF-8 string; enough to prove typed round-trips. */
    static final class IntPayloadDeserializer implements PayloadDeserializer {
        @Override
        public <T> T deserialize(byte[] payload, Class<T> type) {
            int value = Integer.parseInt(new String(payload, StandardCharsets.UTF_8));
            Object event;
            if (type == OrderConfirmed.class) {
                event = new OrderConfirmed(value);
            } else if (type == PaymentRecorded.class) {
                event = new PaymentRecorded(value);
            } else if (type == EntryRecorded.class) {
                event = new EntryRecorded(value);
            } else {
                throw new AssertionError("unexpected type " + type);
            }
            return type.cast(event);
        }
    }

    private static MessageNameRegistry names() {
        return MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .register(PaymentRecorded.class, "order-silo.payment-recorded")
                .register(EntryRecorded.class, "order-silo.entry-recorded")
                .freeze();
    }

    private static ConsumerRegistry registryFor(String id, Class<?> messageClass, Object worker, String methodName) {
        try {
            var registry = new ConsumerRegistry();
            var method = worker.getClass().getDeclaredMethod(methodName, messageClass);
            method.setAccessible(true); // registration's contract: the scanner does this at scan time
            registry.register(new ConsumerDefinition(id, messageClass, worker, method, -1));
            registry.freeze();
            return registry;
        } catch (NoSuchMethodException e) {
            throw new AssertionError("fixture consumer method is missing", e);
        }
    }

    private static Envelope envelope(String messageType, String payload) {
        return new Envelope(
                UUID.randomUUID(),
                messageType,
                Map.of("content-type", "text/plain"),
                payload.getBytes(StandardCharsets.UTF_8));
    }

    private static com.rabbitmq.client.GetResponse awaitParked(String queue, Duration deadline) throws Exception {
        var end = Instant.now().plus(deadline);
        while (Instant.now().isBefore(end)) {
            try (var channel = publishConnection.createChannel()) {
                var response = channel.basicGet(queue, true);
                if (response != null) {
                    return response;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("no message parked in '" + queue + "' within " + deadline);
    }
}
