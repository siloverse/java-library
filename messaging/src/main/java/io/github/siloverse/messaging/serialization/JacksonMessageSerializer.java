package io.github.siloverse.messaging.serialization;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.exception.MessageSerializationException;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores messages as JSON, using the fully qualified class name as type token.
 *
 * <p>Resolved classes are cached and checked against {@link Message} before instantiation, so a
 * tampered {@code message_type} value cannot be used to construct arbitrary classes.
 */
public class JacksonMessageSerializer implements MessageSerializer {

    private final ObjectMapper objectMapper;
    private final ClassLoader classLoader;
    private final Map<String, Class<? extends Message>> typeCache = new ConcurrentHashMap<>();

    public JacksonMessageSerializer(ObjectMapper objectMapper) {
        this(objectMapper, JacksonMessageSerializer.class.getClassLoader());
    }

    public JacksonMessageSerializer(ObjectMapper objectMapper, ClassLoader classLoader) {
        this.objectMapper = objectMapper;
        this.classLoader = classLoader;
    }

    @Override
    public SerializedMessage serialize(Message message) {
        try {
            return new SerializedMessage(
                    message.getClass().getName(),
                    objectMapper.writeValueAsString(message));
        }
        catch (JacksonException ex) {
            throw new MessageSerializationException(
                    "Could not serialize message of type " + message.getClass().getName(), ex);
        }
    }

    @Override
    public Message deserialize(String type, String payload) {
        Class<? extends Message> messageType = resolve(type);
        try {
            return objectMapper.readValue(payload, messageType);
        }
        catch (JacksonException ex) {
            throw new MessageSerializationException(
                    "Could not deserialize stored payload of type " + type, ex);
        }
    }

    private Class<? extends Message> resolve(String type) {
        return typeCache.computeIfAbsent(type, name -> {
            Class<?> resolved;
            try {
                resolved = ClassUtils.forName(name, classLoader);
            }
            catch (ClassNotFoundException | LinkageError ex) {
                throw new MessageSerializationException(
                        "Stored message type " + name + " is not on the classpath", ex);
            }
            if (!Message.class.isAssignableFrom(resolved)) {
                throw new MessageSerializationException(
                        "Stored message type " + name + " does not implement Message");
            }
            return resolved.asSubclass(Message.class);
        });
    }
}
