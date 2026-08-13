package io.github.siloverse.messaging.core.transport;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EnvelopeTest {

    @Test
    void testMutatingInputArrayDoesNotChangePayload() {
        byte[] input = {1, 2, 3};
        var envelope = new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", Map.of(), input);

        input[0] = 99;

        assertThat(envelope.payload()).containsExactly(1, 2, 3);
    }

    @Test
    void testMutatingReturnedArrayDoesNotChangePayload() {
        var envelope = new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", Map.of(),
                new byte[]{1, 2, 3});

        envelope.payload()[0] = 99;

        assertThat(envelope.payload()).containsExactly(1, 2, 3);
    }

    @Test
    void testMutatingInputMapDoesNotChangeHeaders() {
        var input = new HashMap<String, String>();
        input.put("content-type", "application/json");
        var envelope = new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", input, new byte[0]);

        input.put("evil", "late-addition");

        assertThat(envelope.headers()).containsOnlyKeys("content-type");
    }

    @Test
    void testHeadersAreImmutable() {
        var envelope = new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", Map.of(), new byte[0]);

        assertThatThrownBy(() -> envelope.headers().put("evil", "mutation"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testNullComponentsAreRejectedAtConstruction() {
        assertThatThrownBy(() -> new Envelope(null, "order-silo.order-confirmed", Map.of(), new byte[0]))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageId");

        assertThatThrownBy(() -> new Envelope(UUID.randomUUID(), null, Map.of(), new byte[0]))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageType");

        assertThatThrownBy(() -> new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", null, new byte[0]))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", Map.of(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
