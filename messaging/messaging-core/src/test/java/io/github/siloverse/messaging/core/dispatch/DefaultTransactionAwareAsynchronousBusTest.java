package io.github.siloverse.messaging.core.dispatch;

import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.fixtures.ConfirmOrder;
import io.github.siloverse.messaging.core.fixtures.FakePayloadSerializer;
import io.github.siloverse.messaging.core.fixtures.OrderConfirmed;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.OutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DefaultTransactionAwareAsynchronousBusTest {

    MessageNameRegistry registry;
    FakePayloadSerializer serializer;
    RecordingOutbox outbox;
    DefaultTransactionAwareAsynchronousBus bus;

    @BeforeEach
    void setup() {
        registry = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .register(ConfirmOrder.class, "order-silo.confirm-order")
                .freeze();
        serializer = new FakePayloadSerializer();
        outbox = new RecordingOutbox();
        bus = new DefaultTransactionAwareAsynchronousBus(registry, outbox, serializer);
    }

    @Test
    void testPublishAppendsEnvelopeToOutbox() {
        var event = new OrderConfirmed("42");

        bus.publish(event);

        assertThat(outbox.appended).hasSize(1);
        Envelope envelope = outbox.appended.getFirst();
        assertThat(envelope.messageType()).isEqualTo("order-silo.order-confirmed");
        assertThat(envelope.payload()).isEqualTo(event.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(envelope.headers()).containsEntry("content-type", "test/fake");
        assertThat(envelope.messageId()).isNotNull();
    }

    @Test
    void testSendAppendsEnvelopeToOutbox() {
        var command = new ConfirmOrder("42");

        bus.send(command);

        assertThat(outbox.appended).hasSize(1);
        Envelope envelope = outbox.appended.getFirst();
        assertThat(envelope.messageType()).isEqualTo("order-silo.confirm-order");
        assertThat(envelope.payload()).isEqualTo(command.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(envelope.headers()).containsEntry("content-type", "test/fake");
        assertThat(envelope.messageId()).isNotNull();
    }

    @Test
    void testUnregisteredMessageThrowsAndAppendsNothing() {
        var emptyRegistryBus = new DefaultTransactionAwareAsynchronousBus(
                MessageNameRegistry.builder().freeze(), outbox, serializer);

        assertThatThrownBy(() -> emptyRegistryBus.publish(new OrderConfirmed("42")))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(OrderConfirmed.class.getName());

        // failing must not leave a half-built row for the relay to find
        assertThat(outbox.appended).isEmpty();
    }

    @Test
    void testEachPublishMintsAFreshMessageId() {
        bus.publish(new OrderConfirmed("42"));
        bus.publish(new OrderConfirmed("42"));

        assertThat(outbox.appended).hasSize(2);
        assertThat(outbox.appended.getFirst().messageId())
                .isNotEqualTo(outbox.appended.getLast().messageId());
    }

    @Test
    void testConstructorRejectsNullDependencies() {
        assertThatThrownBy(() -> new DefaultTransactionAwareAsynchronousBus(null, outbox, serializer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultTransactionAwareAsynchronousBus(registry, null, serializer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultTransactionAwareAsynchronousBus(registry, outbox, null))
                .isInstanceOf(NullPointerException.class);
    }

    static class RecordingOutbox implements OutboxWriter {

        final List<Envelope> appended = new ArrayList<>();

        @Override
        public void append(Envelope envelope) {
            appended.add(envelope);
        }
    }
}
