package io.github.siloverse.messaging.exception;

/**
 * Thrown during startup when an {@code @Consumer} method is invalid or when a command type has
 * more than one consumer. Fails the application context on purpose.
 */
public class ConsumerDefinitionException extends MessagingException {

    public ConsumerDefinitionException(String message) {
        super(message);
    }
}
