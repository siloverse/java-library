package io.github.siloverse.messaging.exception;

/**
 * Base type for every exception raised by the messaging library itself.
 */
public class MessagingException extends RuntimeException {

    public MessagingException(String message) {
        super(message);
    }

    public MessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
