package io.github.siloverse.messaging.spring.consumer;

import java.util.concurrent.atomic.AtomicBoolean;

import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.consumer.Consumer;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.spring.config.MessagingConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ConsumerBeanEligibilityTest {

    @BeforeEach
    void resetFlag() {
        HarmlessLazyBean.INSTANTIATED.set(false);
    }

    @Test
    void lazyBeanWithConsumerFailsStartup() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(
                MessagingConfiguration.class, LazyConsumerConfig.class))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("lazyConsumer")     // bean name → the fix is findable
                .hasMessageContaining("lazy");            // which of the three fixes applies
    }

    @Test
    void prototypeConsumerFailsStartup() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(
                MessagingConfiguration.class, PrototypeConsumerConfig.class))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("prototypeConsumer")
                .hasMessageContaining("prototype");
    }

    @Test
    void lazyBeanInheritingConsumerFromParentFailsStartup() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(
                MessagingConfiguration.class, LazyInheritedConsumerConfig.class))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("lazyInheritedConsumer")
                .hasMessageContaining("lazy");
    }

    @Test
    void lazyBeanWithoutConsumersStaysLazy() {
        try (var ctx = new AnnotationConfigApplicationContext(
                MessagingConfiguration.class, HarmlessLazyConfig.class)) {
            assertThat(HarmlessLazyBean.INSTANTIATED.get())
                    .as("scanning must inspect the class, never instantiate the bean")
                    .isFalse();
        }
    }

    record TestEvent(String value) implements Event {
    }

    static class LazyConsumerBean {
        @Consumer(id = "eligibility.lazy")
        public void on(TestEvent e) {
        }
    }

    static class PrototypeConsumerBean {
        @Consumer(id = "eligibility.prototype")
        public void on(TestEvent e) {
        }
    }

    abstract static class ParentWithConsumer {
        @Consumer(id = "eligibility.inherited")
        public void on(TestEvent e) {
        }
    }

    // declares nothing itself -- the @Consumer sits on the parent
    static class LazyInheritedConsumerBean extends ParentWithConsumer {
    }

    static class HarmlessLazyBean {
        static final AtomicBoolean INSTANTIATED = new AtomicBoolean(false);

        public HarmlessLazyBean() {
            INSTANTIATED.set(true);
        }
    }

    @Configuration
    static class LazyConsumerConfig {
        @Bean
        @Lazy
        LazyConsumerBean lazyConsumer() {
            return new LazyConsumerBean();
        }
    }

    @Configuration
    static class PrototypeConsumerConfig {
        @Bean
        @Scope(BeanDefinition.SCOPE_PROTOTYPE)
        PrototypeConsumerBean prototypeConsumer() {
            return new PrototypeConsumerBean();
        }
    }

    @Configuration
    static class LazyInheritedConsumerConfig {
        @Bean
        @Lazy
        LazyInheritedConsumerBean lazyInheritedConsumer() {
            return new LazyInheritedConsumerBean();
        }
    }

    @Configuration
    static class HarmlessLazyConfig {
        @Bean
        @Lazy
        HarmlessLazyBean harmlessLazy() {
            return new HarmlessLazyBean();
        }
    }
}
