package io.github.siloverse.messaging.spring.serialization;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;

public class JacksonPayloadSerializer implements PayloadSerializer {

    private final ObjectMapper objectMapper;

    public JacksonPayloadSerializer(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    @Override
    public byte[] serialize(Object message) {
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException exception) {
            // never include the payload itself: it is business data and ends up in logs
            throw new MessagingException(
                    "Could not serialize message of type " + message.getClass().getName()
                            + " to JSON. Ensure the class is Jackson-serializable (public accessors"
                            + " or record components; register modules for types like java.time) --"
                            + " the cause names the failing field.", exception);
        }
    }

    @Override
    public String contentType() {
        return "application/json";
    }
}
