package io.github.siloverse.messaging.consumer;

import java.lang.reflect.Method;

/**
 * Builds identifiers of the form
 * {@code beanName#declaring.Class#methodName(parameter.Type)}.
 *
 * <p>All four parts are needed: the bean name distinguishes two beans of the same class, the
 * declaring class survives bean renaming through inheritance, and method name plus parameter type
 * distinguish overloads within a class.
 */
public final class DefaultConsumerIdStrategy implements ConsumerIdStrategy {

    @Override
    public String consumerId(String beanName, Class<?> beanType, Method method) {
        return beanName
                + "#" + method.getDeclaringClass().getName()
                + "#" + method.getName()
                + "(" + method.getParameterTypes()[0].getName() + ")";
    }
}
