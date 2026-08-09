package io.github.siloverse.messaging.exception;

/**
 * Thrown when a durable message would be written outside a database transaction, which would break
 * the guarantee that business state and messages commit together.
 */
public class TransactionRequiredException extends MessagingException {

    public TransactionRequiredException(String message) {
        super(message);
    }
}
