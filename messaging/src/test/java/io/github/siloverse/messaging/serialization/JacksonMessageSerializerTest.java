package io.github.siloverse.messaging.serialization;

import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.exception.MessageSerializationException;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonMessageSerializerTest {

    private final MessageSerializer serializer = new JacksonMessageSerializer(JsonMapper.builder().build());

    @Test
    void roundTripsAMessageBackToItsConcreteType() {
        OrderConfirmed original = new OrderConfirmed(UUID.randomUUID());

        SerializedMessage serialized = serializer.serialize(original);
        Message restored = serializer.deserialize(serialized.type(), serialized.payload());

        assertThat(serialized.type()).isEqualTo(OrderConfirmed.class.getName());
        assertThat(restored).isInstanceOf(OrderConfirmed.class).isEqualTo(original);
    }

    @Test
    void rejectsUnknownTypes() {
        assertThatThrownBy(() -> serializer.deserialize("com.example.Gone", "{}"))
                .isInstanceOf(MessageSerializationException.class)
                .hasMessageContaining("not on the classpath");
    }

    @Test
    void rejectsTypesThatAreNotMessages() {
        assertThatThrownBy(() -> serializer.deserialize(String.class.getName(), "\"hello\""))
                .isInstanceOf(MessageSerializationException.class)
                .hasMessageContaining("does not implement Message");
    }

    @Test
    void rejectsUnreadablePayloads() {
        assertThatThrownBy(() -> serializer.deserialize(OrderConfirmed.class.getName(), "not json"))
                .isInstanceOf(MessageSerializationException.class)
                .hasMessageContaining("Could not deserialize");
    }
}
