package io.github.siloverse.messaging.config;

import io.github.siloverse.messaging.persistence.entity.StoredMessage;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.persistence.autoconfigure.EntityScanPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the messaging entities to the application's persistence unit.
 *
 * <p>Registering entity scan packages replaces Spring Boot's default of scanning the application
 * package, so when the application has not configured {@code @EntityScan} itself, that default is
 * re-added explicitly. Without this the library's tables would be invisible to Hibernate, or worse,
 * the application's own entities would silently disappear.
 */
class MessagingEntityRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

    private static final String ENTITY_SCAN_PACKAGES_BEAN = EntityScanPackages.class.getName();

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        List<String> packages = new ArrayList<>();
        boolean alreadyConfigured = registry.containsBeanDefinition(ENTITY_SCAN_PACKAGES_BEAN);
        if (!alreadyConfigured && AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        packages.add(StoredMessage.class.getPackageName());
        EntityScanPackages.register(registry, packages);
    }
}
