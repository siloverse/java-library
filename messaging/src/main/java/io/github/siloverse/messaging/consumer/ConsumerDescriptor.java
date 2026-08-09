package io.github.siloverse.messaging.consumer;

import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.api.MessageKind;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Immutable description of one discovered {@code @Consumer} method.
 *
 * @param consumerId  stable identifier persisted on durable deliveries
 * @param beanName    name of the Spring bean declaring the method
 * @param beanType    user class of that bean, proxies unwrapped
 * @param method      the annotated method
 * @param messageType the single parameter type
 * @param kind        whether the parameter is a command or an event
 * @param order       relative order among consumers of the same event type
 */
public record ConsumerDescriptor(
        String consumerId,
        String beanName,
        Class<?> beanType,
        Method method,
        Class<? extends Message> messageType,
        MessageKind kind,
        int order) {

    public ConsumerDescriptor {
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(beanName, "beanName");
        Objects.requireNonNull(beanType, "beanType");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(messageType, "messageType");
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * @return a short human readable description, useful in logs and error messages
     */
    public String describe() {
        return beanType.getSimpleName() + "#" + method.getName()
                + "(" + messageType.getSimpleName() + ")";
    }
}
