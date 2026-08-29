package io.github.siloverse.messaging.core.dispatch;

import java.util.Map;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.error.ConsumerInvocationException;

public interface MessageDispatcher {
    void dispatch(ConsumerDefinition def, Map<String, String> messageHeaders, Object message);

    default RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException re) {
            return re;                              // unchecked → rethrow as-is
        }
        if (cause instanceof Error error) {
            throw error;                            // OutOfMemoryError etc. → never wrap, let it fly
        }
        return new ConsumerInvocationException(     // checked → wrap once
                "Consumer threw a checked exception", cause);
    }
}
