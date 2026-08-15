package io.github.siloverse.messaging.spring.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.error.MessagingException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class JacksonPayloadDeserializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record OrderConfirmed(int orderId, String customer) {
    }

    @Test
    void roundTripsWhatTheSerializerProduced() {
        var serializer = new JacksonPayloadSerializer(objectMapper);
        var deserializer = new JacksonPayloadDeserializer(objectMapper);
        var original = new OrderConfirmed(42, "ada");

        var restored = deserializer.deserialize(serializer.serialize(original), OrderConfirmed.class);

        // both sides injected with the same ObjectMapper: what one writes, the other reads
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void malformedBytesFailNamingTheTargetTypeNeverTheBytes() {
        var deserializer = new JacksonPayloadDeserializer(objectMapper);
        var garbage = "not json {{{".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> deserializer.deserialize(garbage, OrderConfirmed.class))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining(OrderConfirmed.class.getName())
                .hasMessageNotContaining("not json");
    }

    @Test
    void nullObjectMapperIsRejectedAtConstruction() {
        assertThatNullPointerException()
                .isThrownBy(() -> new JacksonPayloadDeserializer(null))
                .withMessageContaining("objectMapper");
    }
}
