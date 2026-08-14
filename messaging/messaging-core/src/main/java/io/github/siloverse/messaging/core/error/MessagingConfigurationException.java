package io.github.siloverse.messaging.core.error;

public class MessagingConfigurationException extends MessagingException {
    public MessagingConfigurationException(String message) {
        super(message);
    }

    public MessagingConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
