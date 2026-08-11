package io.github.siloverse.messaging.core.error;

public class ConsumerInvocationException extends RuntimeException {
    public ConsumerInvocationException(String message) { super(message); }
    public ConsumerInvocationException(String message, Throwable cause) { super(message, cause); }
}
