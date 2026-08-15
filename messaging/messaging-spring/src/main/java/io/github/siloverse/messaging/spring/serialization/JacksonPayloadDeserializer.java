package io.github.siloverse.messaging.spring.serialization;

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.transport.PayloadDeserializer;

public class JacksonPayloadDeserializer implements PayloadDeserializer {
    private final ObjectMapper objectMapper;

    public JacksonPayloadDeserializer(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T deserialize(byte[] payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (IOException e) {
            // never include the payload bytes: they are business data and end up in logs
            throw new MessagingException(
                    "Could not deserialize payload into " + type.getName()
                            + ". Ensure the class is Jackson-deserializable (record components or a"
                            + " matching constructor; register modules for types like java.time) --"
                            + " the cause names the failing field.", e);
        }
    }
}
