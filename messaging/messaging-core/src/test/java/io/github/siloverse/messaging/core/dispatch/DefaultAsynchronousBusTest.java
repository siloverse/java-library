package io.github.siloverse.messaging.core.dispatch;

import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.fixtures.ConfirmOrder;
import io.github.siloverse.messaging.core.fixtures.FakePayloadSerializer;
import io.github.siloverse.messaging.core.fixtures.OrderConfirmed;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.EnvelopeFactory;
import io.github.siloverse.messaging.core.transport.EnvelopeHeadersContributor;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DefaultAsynchronousBusTest {

    MessageNameRegistry registry;
    FakePayloadSerializer serializer;
    RecordingTransport transport;
    DefaultAsynchronousBus bus;
    EnvelopeFactory envelopeFactory;
    EnvelopeHeadersContributor envelopeHeadersContributor = new FakeEnvelopeHeadersContributor();

    @BeforeEach
    void setup() {
        registry = MessageNameRegistry.builder().register(OrderConfirmed.class, "order-silo.order-confirmed")
                .register(ConfirmOrder.class, "order-silo.confirm-order").freeze();
        serializer = new FakePayloadSerializer();
        transport = new RecordingTransport();
        envelopeFactory = new EnvelopeFactory(registry, envelopeHeadersContributor, serializer);
        bus = new DefaultAsynchronousBus(envelopeFactory, transport);
    }

    @Test
    void testPublishBuildsEnvelopeFromRegistryAndSerializer() {
        var event = new OrderConfirmed("42");

        bus.publish(event);

        assertThat(transport.sent).hasSize(1);
        Envelope envelope = transport.sent.getFirst();
        assertThat(envelope.messageType()).isEqualTo("order-silo.order-confirmed");
        assertThat(envelope.payload()).isEqualTo(event.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(envelope.headers()).containsEntry("content-type", "test/fake");
        assertThat(envelope.messageId()).isNotNull();
    }

    @Test
    void testSendBuildsEnvelopeFromRegistryAndSerializer() {
        var command = new ConfirmOrder("42");

        bus.send(command);

        assertThat(transport.sent).hasSize(1);
        Envelope envelope = transport.sent.getFirst();
        assertThat(envelope.messageType()).isEqualTo("order-silo.confirm-order");
        assertThat(envelope.payload()).isEqualTo(command.toString().getBytes(StandardCharsets.UTF_8));
        assertThat(envelope.headers()).containsEntry("content-type", "test/fake");
        assertThat(envelope.messageId()).isNotNull();
    }

    @Test
    void testUnregisteredMessageThrowsAndSendsNothing() {
        var envelopeFactory =
                new EnvelopeFactory(MessageNameRegistry.builder().freeze(), envelopeHeadersContributor, serializer);
        var unregisteredOnlyBus = new DefaultAsynchronousBus(envelopeFactory, transport);

        assertThatThrownBy(() -> unregisteredOnlyBus.publish(new OrderConfirmed("42"))).isInstanceOf(
                MessagingConfigurationException.class).hasMessageContaining(OrderConfirmed.class.getName());

        // failing must not leak a half-built message to the wire
        assertThat(transport.sent).isEmpty();
    }

    @Test
    void testEachPublishMintsAFreshMessageId() {
        bus.publish(new OrderConfirmed("42"));
        bus.publish(new OrderConfirmed("42"));

        assertThat(transport.sent).hasSize(2);
        assertThat(transport.sent.getFirst().messageId()).isNotEqualTo(transport.sent.getLast().messageId());
    }

    @Test
    void testConstructorRejectsNullDependencies() {

        assertThatThrownBy(
                () -> new DefaultAsynchronousBus(new EnvelopeFactory(null, envelopeHeadersContributor, serializer),
                        transport)).isInstanceOf(
                NullPointerException.class);
        assertThatThrownBy(
                () -> new DefaultAsynchronousBus(new EnvelopeFactory(registry, envelopeHeadersContributor, serializer),
                        null)).isInstanceOf(
                NullPointerException.class);
        assertThatThrownBy(
                () -> new DefaultAsynchronousBus(new EnvelopeFactory(registry, null, serializer),
                        transport)).isInstanceOf(
                NullPointerException.class);
        assertThatThrownBy(
                () -> new DefaultAsynchronousBus(new EnvelopeFactory(registry, envelopeHeadersContributor, null),
                        transport)).isInstanceOf(
                NullPointerException.class);
    }

    static class RecordingTransport implements MessageTransport {

        final List<Envelope> sent = new ArrayList<>();

        @Override
        public void send(Envelope envelope) {
            sent.add(envelope);
        }
    }

    static class FakeEnvelopeHeadersContributor implements EnvelopeHeadersContributor {

        @Override
        public void contribute(Map<String, String> headers) {
        }
    }
}
