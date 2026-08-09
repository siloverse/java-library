package io.github.siloverse.messaging.exception;

/**
 * Thrown when a message cannot be written to, or reconstructed from, its stored representation.
 */
public class MessageSerializationException extends NonRetryableMessagingException {

    public MessageSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageSerializationException(String message) {
        super(message);
    }
}
