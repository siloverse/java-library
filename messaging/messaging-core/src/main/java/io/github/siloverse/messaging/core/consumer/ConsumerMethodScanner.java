package io.github.siloverse.messaging.core.consumer;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import io.github.siloverse.messaging.core.api.MessageKind;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;

public final class ConsumerMethodScanner {

    /**
     * Inspects every method of the bean; returns a definition for each {@code @Consumer} method. Throws
     * MessagingConfigurationException on any malformed consumer — fail at startup, not at dispatch
     * NOTE: Please don't pass CGLIB-proxied bean.
     */
    List<ConsumerDefinition> scan(Object bean) {
        return scan(bean, bean.getClass());
    }

    public List<ConsumerDefinition> scan(Object invokeTarget, Class<?> scanClass) {

        rejectConsumersDeclaredOnParentClasses(scanClass);

        return Arrays.stream(scanClass.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Consumer.class))
                .map(m -> toConsumerDefinition(invokeTarget, m))
                .toList();
    }

    /**
     * The single definition of "declares consumers": first {@code @Consumer} method declared on the class itself or
     * any ancestor below {@code Object}; empty if none. Used here to reject parent-class declarations and by lifecycle
     * managers to detect consumers on beans that are never scanned (lazy/prototype).
     */
    public static Optional<Method> findDeclaredConsumerMethod(Class<?> clazz) {
        for (var c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var method : c.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Consumer.class)) {
                    return Optional.of(method);
                }
            }
        }
        return Optional.empty();
    }

    private void rejectConsumersDeclaredOnParentClasses(Class<?> childClass) {
        findDeclaredConsumerMethod(childClass.getSuperclass())
                .ifPresent(method -> {
                    var location =
                            method.getDeclaringClass().getSimpleName()
                                    + "#"
                                    + method.getName();

                    throw new MessagingConfigurationException(
                            "@Consumer method " + location
                                    + " is declared on a parent class of "
                                    + childClass.getSimpleName()
                                    + ". @Consumer methods must be declared directly "
                                    + "on the concrete consumer class. Remove @Consumer "
                                    + "from the parent and annotate the child's override.");
                });
    }

    ConsumerDefinition toConsumerDefinition(Object bean, Method consumerMethod) {
        var location = consumerMethod.getDeclaringClass().getSimpleName() + "#" + consumerMethod.getName();
        var consumerAnnotation = consumerMethod.getAnnotation(Consumer.class);

        // 1. id present and non-blank — name the method, the ctor can't
        var id = consumerAnnotation.id();
        if (id.isBlank()) {
            throw new MessagingConfigurationException(
                    "@Consumer on " + location + " has a blank id. The id is the consumer's stable "
                            + "identity (queue name, consumer group, inbox key) and must be explicit.");
        }

        // 2. exactly one parameter — the message
        var parameters = Arrays.stream(consumerMethod.getParameterTypes()).toList();
        if (parameters.size() != 1) {
            throw new MessagingConfigurationException(
                    "@Consumer method " + location + " must take exactly one parameter (the message), "
                            + "but takes " + parameters.size() + ".");
        }
        var messageClass = parameters.getFirst();

        // 3. exactly one marker interface
        MessageKind.of(messageClass);

        // 4. return type discipline
        if (consumerMethod.getReturnType() != void.class) {
            throw new MessagingConfigurationException(
                    "@Consumer method " + location + " should return VOID");
        }

        // 5. all checks passed — only now mutate
        consumerMethod.setAccessible(true);

        return new ConsumerDefinition(id, messageClass, bean, consumerMethod, -1, consumerAnnotation.dedup());
    }
}
