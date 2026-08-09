package io.github.siloverse.messaging.consumer;

import java.lang.reflect.Method;

/**
 * Produces the stable identifier stored on every durable delivery row.
 *
 * <p>The identifier must survive application restarts: pending deliveries written yesterday are
 * matched back to consumer methods by this value. It is deliberately pluggable so the scheme can
 * evolve, for example towards an explicit identifier declared on the annotation.
 */
@FunctionalInterface
public interface ConsumerIdStrategy {

    /**
     * @param beanName name of the declaring Spring bean
     * @param beanType user class of that bean
     * @param method   the annotated method
     * @return an identifier, stable across restarts and unique within an application
     */
    String consumerId(String beanName, Class<?> beanType, Method method);
}
