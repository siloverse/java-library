package io.github.siloverse.messaging.core.consumer;

import java.lang.reflect.Method;
import java.util.Objects;

public record ConsumerDefinition(
        String id,                 // stable identity — Kafka group, queue name, inbox key, all at once
        Class<?> messageClass,     // what it consumes
        Object bean,               // target instance
        Method method,             // target method
        int contextParameterIndex, // -1 for now; where MessageContext sits, later
        boolean dedup              // opt-in inbox deduplication; false = handler declares itself idempotent
) {
    /** Without the dedup flag: the consumer declares itself idempotent (the default). */
    public ConsumerDefinition(String id, Class<?> messageClass, Object bean, Method method,
            int contextParameterIndex) {
        this(id, messageClass, bean, method, contextParameterIndex, false);
    }

    public ConsumerDefinition {
        Objects.requireNonNull(id);
        Objects.requireNonNull(messageClass);
        Objects.requireNonNull(bean);
        Objects.requireNonNull(method);
        if (id.isBlank()) {
            throw new IllegalArgumentException("Consumer id must not be blank");
        }
    }
}
