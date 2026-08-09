package io.github.siloverse.messaging.serialization;

import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.exception.MessageSerializationException;

/**
 * Converts messages to and from their durable representation.
 *
 * <p>Both the type token and the payload format are owned by the implementation, so the rest of the
 * library never sees a serialization library type.
 */
public interface MessageSerializer {

    /**
     * @param message the message to store
     * @return its type token and payload
     * @throws MessageSerializationException if the message cannot be written
     */
    SerializedMessage serialize(Message message);

    /**
     * @param type    the type token produced by {@link #serialize}
     * @param payload the payload produced by {@link #serialize}
     * @return the reconstructed message, of its original concrete type
     * @throws MessageSerializationException if the message cannot be reconstructed
     */
    Message deserialize(String type, String payload);
}
