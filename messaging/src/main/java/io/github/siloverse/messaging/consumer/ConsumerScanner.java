package io.github.siloverse.messaging.consumer;

import io.github.siloverse.messaging.annotation.Consumer;
import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.api.MessageKind;
import io.github.siloverse.messaging.exception.ConsumerDefinitionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds {@code @Consumer} methods on the beans of an application context and validates them.
 *
 * <p>Bean types are resolved without instantiating the beans, in the same way Spring discovers
 * {@code @EventListener} methods.
 */
public class ConsumerScanner {

    private static final Log logger = LogFactory.getLog(ConsumerScanner.class);

    private final ConsumerIdStrategy idStrategy;

    public ConsumerScanner(ConsumerIdStrategy idStrategy) {
        this.idStrategy = idStrategy;
    }

    /**
     * Scans every bean definition of the given factory.
     *
     * @param beanFactory the bean factory to inspect
     * @return all valid consumer descriptors, in bean definition order
     * @throws ConsumerDefinitionException if any annotated method is invalid
     */
    public List<ConsumerDescriptor> scan(ConfigurableListableBeanFactory beanFactory) {
        List<ConsumerDescriptor> descriptors = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = resolveBeanType(beanFactory, beanName);
            if (beanType != null) {
                descriptors.addAll(scanBean(beanName, beanType));
            }
        }
        return List.copyOf(descriptors);
    }

    /**
     * Scans a single class as if it were registered under the given bean name. Mostly useful for
     * tests and for programmatic registration.
     *
     * @param beanName the bean name to record on the descriptors
     * @param beanType the class to inspect
     * @return the descriptors found on that class
     */
    public List<ConsumerDescriptor> scanBean(String beanName, Class<?> beanType) {
        Class<?> userType = ClassUtils.getUserClass(beanType);
        List<ConsumerDescriptor> descriptors = new ArrayList<>();
        ReflectionUtils.doWithMethods(userType, method -> {
            Consumer annotation = AnnotatedElementUtils.findMergedAnnotation(method, Consumer.class);
            if (annotation != null) {
                descriptors.add(describe(beanName, userType, method, annotation));
            }
        }, ReflectionUtils.USER_DECLARED_METHODS);
        return descriptors;
    }

    private ConsumerDescriptor describe(String beanName, Class<?> beanType, Method method, Consumer annotation) {
        validate(beanType, method);
        @SuppressWarnings("unchecked")
        Class<? extends Message> messageType = (Class<? extends Message>) method.getParameterTypes()[0];
        return new ConsumerDescriptor(
                idStrategy.consumerId(beanName, beanType, method),
                beanName,
                beanType,
                method,
                messageType,
                MessageKind.of(messageType),
                annotation.order());
    }

    private void validate(Class<?> beanType, Method method) {
        String where = beanType.getName() + "#" + method.getName();

        if (Modifier.isStatic(method.getModifiers())) {
            throw new ConsumerDefinitionException(
                    "@Consumer method " + where + " must not be static.");
        }
        if (method.getParameterCount() != 1) {
            throw new ConsumerDefinitionException(
                    "@Consumer method " + where + " must declare exactly one argument but declares "
                            + method.getParameterCount() + ".");
        }
        if (!void.class.equals(method.getReturnType())) {
            throw new ConsumerDefinitionException(
                    "@Consumer method " + where + " must return void but returns "
                            + method.getReturnType().getName() + ".");
        }

        Class<?> parameterType = method.getParameterTypes()[0];
        if (!Message.class.isAssignableFrom(parameterType)) {
            throw new ConsumerDefinitionException(
                    "@Consumer method " + where + " must accept a Command or an Event but accepts "
                            + parameterType.getName() + ".");
        }
        if (MessageKind.of(parameterType) == null) {
            throw new ConsumerDefinitionException(
                    "@Consumer method " + where + " accepts " + parameterType.getName()
                            + ", which must implement exactly one of Command or Event.");
        }
        if (parameterType.isInterface() || Modifier.isAbstract(parameterType.getModifiers())) {
            throw new ConsumerDefinitionException(
                    "@Consumer method " + where + " accepts the abstract type " + parameterType.getName()
                            + ". Consumers are matched on the concrete message type, so this method "
                            + "could never be invoked.");
        }
    }

    private Class<?> resolveBeanType(ConfigurableListableBeanFactory beanFactory, String beanName) {
        try {
            Class<?> type = beanFactory.getType(beanName);
            if (type == null || isIgnored(type)) {
                return null;
            }
            return type;
        }
        catch (Throwable ex) {
            if (logger.isDebugEnabled()) {
                logger.debug("Could not resolve the type of bean '" + beanName
                        + "' while scanning for @Consumer methods", ex);
            }
            return null;
        }
    }

    private boolean isIgnored(Class<?> type) {
        // Scanning framework internals is pointless and can trigger unwanted class loading.
        if (!AnnotationUtils.isCandidateClass(type, Consumer.class)) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("org.springframework.") || name.startsWith("jakarta.");
    }
}
