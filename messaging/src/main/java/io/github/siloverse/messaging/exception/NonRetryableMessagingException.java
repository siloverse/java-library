package io.github.siloverse.messaging.exception;

/**
 * Signals a failure that retrying cannot fix, such as an unreadable payload or a consumer that no
 * longer exists. Durable deliveries failing with this exception go straight to
 * {@code FAILED} instead of being rescheduled.
 */
public class NonRetryableMessagingException extends MessagingException {

    public NonRetryableMessagingException(String message) {
        super(message);
    }

    public NonRetryableMessagingException(String message, Throwable cause) {
        super(message, cause);
    }
}
