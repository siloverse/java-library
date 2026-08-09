package io.github.siloverse.messaging.exception;

/**
 * Thrown when a stored delivery references a consumer that is no longer present in the running
 * application, typically because the method was renamed or removed while deliveries were pending.
 */
public class UnknownConsumerException extends NonRetryableMessagingException {

    public UnknownConsumerException(String consumerId) {
        super("No consumer is registered under id '" + consumerId
                + "'. The consumer method was probably renamed, moved or removed.");
    }
}
