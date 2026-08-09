package io.github.siloverse.messaging.consumer;

import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.exception.MessagingException;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;

/**
 * Invokes a consumer method on its Spring bean.
 *
 * <p>The bean is looked up at invocation time, so proxies, scoped beans and lazy beans behave as
 * they would for any other Spring managed call. Exceptions thrown by the consumer are unwrapped and
 * rethrown unchanged, which is what synchronous callers expect and what the durable processor uses
 * to decide whether to retry.
 */
public class ConsumerInvoker {

    private final BeanFactory beanFactory;

    public ConsumerInvoker(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /**
     * @param descriptor the consumer to call
     * @param message    the message to pass, already of the descriptor's message type
     */
    public void invoke(ConsumerDescriptor descriptor, Message message) {
        Object bean = beanFactory.getBean(descriptor.beanName());
        Method method = AopUtils.selectInvocableMethod(descriptor.method(), bean.getClass());
        ReflectionUtils.makeAccessible(method);
        try {
            method.invoke(bean, message);
        }
        catch (InvocationTargetException ex) {
            throw rethrow(ex.getTargetException(), descriptor);
        }
        catch (IllegalAccessException | IllegalArgumentException ex) {
            throw new MessagingException("Could not invoke consumer " + descriptor.describe(), ex);
        }
    }

    private RuntimeException rethrow(Throwable cause, ConsumerDescriptor descriptor) {
        if (cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        throw new UndeclaredThrowableException(cause, "Consumer " + descriptor.describe() + " failed");
    }
}
