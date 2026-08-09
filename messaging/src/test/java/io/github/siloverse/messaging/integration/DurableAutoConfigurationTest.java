package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.persistence.DurableMessageStore;
import io.github.siloverse.messaging.transaction.TransactionAwareAsynchronousCommandBus;
import io.github.siloverse.messaging.transaction.TransactionAwareAsynchronousEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.task.TaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks how the library sits inside a real application context.
 */
class DurableAutoConfigurationTest extends AbstractDurableMessagingTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private EventBus eventBus;

    @Test
    void theConfiguredModeSelectsTheTransactionAwareBuses() {
        assertThat(AopUtils.getTargetClass(commandBus)).isEqualTo(TransactionAwareAsynchronousCommandBus.class);
        assertThat(AopUtils.getTargetClass(eventBus)).isEqualTo(TransactionAwareAsynchronousEventBus.class);
    }

    @Test
    void durableInfrastructureIsAvailable() {
        assertThat(context.getBean(DurableMessageStore.class)).isNotNull();
        assertThat(context.getBean("messagingTaskExecutor")).isInstanceOf(TaskExecutor.class);
    }

    @Test
    void springBootsOwnTaskExecutorIsNotSuppressed() {
        // The library's worker pool is a non default candidate precisely so that Spring Boot's
        // @ConditionalOnMissingBean(Executor.class) still creates applicationTaskExecutor.
        assertThat(context.containsBean("applicationTaskExecutor")).isTrue();
    }

    @Test
    void theApplicationsOwnEntitiesAndRepositoriesStillWork() {
        // Registering the library's entities must not hide the application's own ones.
        orders.save(new TestOrder(java.util.UUID.randomUUID(), "NEW"));

        assertThat(orders.count()).isEqualTo(1);
    }
}
