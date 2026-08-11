package io.github.siloverse.messaging.spring.consumer;

import io.github.siloverse.messaging.core.consumer.ConsumerMethodScanner;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.ClassUtils;

/**
 * Bridges Spring's lifecycle to the messaging registry: after every singleton exists (fully initialized and proxied),
 * scans them for {@code @Consumer} methods, registers the definitions, and freezes the registry.
 *
 * <p>Runs at {@link SmartInitializingSingleton#afterSingletonsInstantiated()} --
 * after all singletons, before ApplicationReadyEvent and runners. This is the same lifecycle slot Spring's own
 * {@code EventListenerMethodProcessor} uses to register {@code @EventListener} methods, and for the same reason: it is
 * the earliest moment at which no consumer bean can be missed, and the latest moment that is still before application
 * code starts publishing.
 *
 * <p>A {@link MessagingConfigurationException} thrown here (malformed consumer,
 * duplicate id, duplicate command consumer) aborts context startup: configuration errors fail the deploy, not the 3am
 * dispatch.
 */
public class ConsumerScanningInitializer implements BeanFactoryPostProcessor, SmartInitializingSingleton {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerScanningInitializer.class);

    private ConfigurableListableBeanFactory beanFactory;
    private final SpringConsumerScanner scanner;
    private final ConsumerRegistry registry;

    public ConsumerScanningInitializer(
            SpringConsumerScanner scanner,
            ConsumerRegistry registry
    ) {
        this.scanner = scanner;
        this.registry = registry;
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
        registry.beginInitialization();
    }

    @Override
    public void afterSingletonsInstantiated() {
        int consumerCount = 0;
        int scannedBeans = 0;

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            var beanDefinition = beanFactory.getBeanDefinition(beanName);

            // Abstract definitions are templates, not beans -- getBean() would throw.
            if (beanDefinition.isAbstract()) {
                continue;
            }

            boolean eligible = beanDefinition.isSingleton() && !beanDefinition.isLazyInit();
            if (!eligible) {
                // Do NOT getBean() — that would force-create the lazy bean.
                // Inspect the CLASS for @Consumer methods instead:
                Class<?> beanType = beanFactory.getType(beanName, false);   // allowInit=false: no factory-bean init
                if (beanType != null && hasConsumerMethods(beanType)) {
                    throw new MessagingConfigurationException(
                            "Bean '" + beanName + "' (" + beanType.getName() + ") declares @Consumer "
                                    + "methods but is " + describeScope(beanDefinition) + ". Consumers must be "
                                    + "eager singletons: a lazy consumer would silently receive nothing until "
                                    + "first unrelated use, and a prototype has no single instance to dispatch to. "
                                    + "Remove @Lazy / make the bean a singleton, or move the @Consumer methods "
                                    + "to an eager singleton bean.");
                }
                continue;   // lazy/prototype WITHOUT consumers: none of our business
            }

            Object bean = beanFactory.getBean(beanName);
            var definitions = scanner.scan(bean);

            for (var definition : definitions) {
                registry.register(definition);
                logger.info("Registered consumer '{}' -> {}", definition.id(),
                        definition.method().getDeclaringClass().getSimpleName()
                                + "#" + definition.method().getName());
                consumerCount++;
            }
            scannedBeans++;
        }
        registry.freeze();

        logger.info("Messaging initialized: {} consumer(s) registered from {} bean(s); registry frozen",
                consumerCount, scannedBeans);
    }

    private boolean hasConsumerMethods(Class<?> beanType) {
        var targetClass = ClassUtils.getUserClass(beanType); // type may already be proxy class
        return ConsumerMethodScanner.findDeclaredConsumerMethod(targetClass).isPresent();
    }

    private String describeScope(BeanDefinition def) {
        if (def.isLazyInit()) {
            return "lazy (@Lazy)";
        }
        if (!def.isSingleton()) {
            return "scope '" + def.getScope() + "'";
        }
        return "not an eager singleton";
    }
}
