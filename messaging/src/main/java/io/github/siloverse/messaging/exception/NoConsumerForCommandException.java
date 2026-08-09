package io.github.siloverse.messaging.exception;

/**
 * Thrown when a command is sent but no {@code @Consumer} method accepts its type.
 */
public class NoConsumerForCommandException extends MessagingException {

    public NoConsumerForCommandException(Class<?> commandType) {
        super("No @Consumer method is registered for command " + commandType.getName()
                + ". A command must have exactly one consumer.");
    }
}
