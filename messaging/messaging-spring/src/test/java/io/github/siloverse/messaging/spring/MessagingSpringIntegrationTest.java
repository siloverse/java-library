package io.github.siloverse.messaging.spring;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.api.SynchronousBus;
import io.github.siloverse.messaging.core.consumer.Consumer;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.error.MessagingException;

import jakarta.annotation.PostConstruct;

import io.github.siloverse.messaging.spring.config.MessagingConfiguration;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagingSpringIntegrationTest {

    @Test
    void transactionalConsumerIsFoundAndInvokedThroughItsProxy() {
        try (var ctx = new AnnotationConfigApplicationContext(
                MessagingConfiguration.class, TxConfig.class)) {

            var handler = ctx.getBean(TransactionalHandler.class);

            // Precondition, or this test proves nothing: the bean really is proxied.
            assertThat(AopUtils.isCglibProxy(handler)).isTrue();

            ctx.getBean(SynchronousBus.class).publish(new TestEvent("hello"));

            // Trap 2, both halves:
            assertThat(handler.received()).hasSize(1);                    // found despite proxy
            assertThat(handler.txActiveDuringConsume()).isTrue();         // invoked THROUGH proxy

            assertThat(ctx.getBean(ConsumerRegistry.class).isFrozen()).isTrue();
        }
    }

    // FAILS against current code: @PostConstruct runs before beginInitialization(),
    // registry is OPEN, publish silently hits an empty registry and the context
    // starts. Green only after the BeanFactoryPostProcessor fix arms the guard
    // before any singleton exists. Spring wraps init-method failures, so the
    // messaging error sits in the root cause.
    @Test
    void publishingFromPostConstructFailsStartupWithHelpfulMessage() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(
                MessagingConfiguration.class, EagerPublisherConfig.class))
                .isInstanceOf(BeanCreationException.class)
                .rootCause()
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("initializing");
    }

    record TestEvent(String value) implements Event {
    }

    /**
     * Minimal real transaction manager: begin/commit are no-ops, but the abstract base does the
     * TransactionSynchronizationManager bookkeeping -- so isActualTransactionActive() is genuinely true inside
     *
     * @Transactional methods. No database needed to prove the proxy path works.
     */
    private static class RecordingTxManager extends AbstractPlatformTransactionManager {
        @Serial
        private static final long serialVersionUID = 1L;   // base is Serializable; -Werror

        @Override
        protected @NonNull Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(@NonNull Object tx, @NonNull TransactionDefinition def) {
        }

        @Override
        protected void doCommit(@NonNull DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(@NonNull DefaultTransactionStatus status) {
        }
    }

    static class TransactionalHandler {
        private final List<TestEvent> received = new ArrayList<>();
        private volatile boolean txActiveDuringConsume;

        @Transactional
        @Consumer(id = "it.transactional-consumer")
        public void on(TestEvent e) {
            txActiveDuringConsume = TransactionSynchronizationManager.isActualTransactionActive();
            received.add(e);
        }

        // Read state via METHODS, never fields: Spring instantiates CGLIB proxies
        // through Objenesis, skipping the constructor -- the PROXY's fields are null.
        // Method calls route through to the target, whose fields are real.
        // One more face of Trap 2.
        public List<TestEvent> received() {
            return received;
        }

        public boolean txActiveDuringConsume() {
            return txActiveDuringConsume;
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TxConfig {
        @Bean
        PlatformTransactionManager txManager() {
            return new RecordingTxManager();
        }

        @Bean
        TransactionalHandler transactionalHandler() {
            return new TransactionalHandler();
        }
    }

    static class EagerPublisher {
        private final SynchronousBus bus;

        public EagerPublisher(SynchronousBus bus) {
            this.bus = bus;
        }

        @PostConstruct
        void publishTooEarly() {
            bus.publish(new TestEvent("too early"));
        }
    }

    @Configuration
    static class EagerPublisherConfig {
        @Bean
        EagerPublisher eagerPublisher(SynchronousBus bus) {
            return new EagerPublisher(bus);
        }
    }
}