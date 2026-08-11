package io.github.siloverse.messaging.core.error;

public class MessagingException extends RuntimeException {
    public MessagingException(String message) { super(message); }
    public MessagingException(String message, Throwable cause) { super(message, cause); }
}
