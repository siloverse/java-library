package io.github.siloverse.messaging.config;

import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.async.MessagePoller;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.dispatch.CommandDispatcher;
import io.github.siloverse.messaging.exception.ConsumerDefinitionException;
import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.fixture.RecordingConsumers;
import io.github.siloverse.messaging.persistence.DurableMessageStore;
import io.github.siloverse.messaging.serialization.MessageSerializer;
import io.github.siloverse.messaging.sync.SynchronousCommandBus;
import io.github.siloverse.messaging.sync.SynchronousEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Auto-configuration without a database: only the synchronous half is expected to appear.
 */
class MessagingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MessagingAutoConfiguration.class))
            .withUserConfiguration(ConsumerConfiguration.class);

    @Test
    void registersConsumerDiscoveryAndSynchronousBusesByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ConsumerRegistry.class);
            assertThat(context).hasSingleBean(CommandDispatcher.class);
            assertThat(context).hasSingleBean(MessageSerializer.class);
            assertThat(context).getBean(CommandBus.class).isInstanceOf(SynchronousCommandBus.class);
            assertThat(context).getBean(EventBus.class).isInstanceOf(SynchronousEventBus.class);
        });
    }

    @Test
    void skipsDurableInfrastructureWithoutADataSource() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DurableMessageStore.class);
            assertThat(context).doesNotHaveBean(MessagePoller.class);
            assertThat(context).doesNotHaveBean(TaskExecutor.class);
        });
    }

    @Test
    void discoversConsumersAndDispatchesThroughTheAutoConfiguredBuses() {
        runner.run(context -> {
            RecordingConsumers consumers = context.getBean(RecordingConsumers.class);
            UUID orderId = UUID.randomUUID();

            context.getBean(CommandBus.class).send(MessageProvider.of(new ConfirmOrder(orderId)));
            context.getBean(EventBus.class).publish(MessageProvider.of(new OrderConfirmed(orderId)));

            assertThat(consumers.commands()).hasSize(1);
            assertThat(consumers.emails()).hasSize(1);
            assertThat(consumers.analytics()).hasSize(1);
        });
    }

    @Test
    void failsStartupWhenADurableModeHasNoDatabase() {
        runner.withPropertyValues("messaging.event.mode=transactional_async")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsStartupWhenACommandHasTwoConsumers() {
        runner.withUserConfiguration(DuplicateCommandConsumerConfiguration.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(ConsumerDefinitionException.class)
                    .hasMessageContaining("must have exactly one @Consumer method");
        });
    }

    @Test
    void applicationBeansOverrideLibraryBeans() {
        runner.withUserConfiguration(CustomBusConfiguration.class)
                .run(context -> assertThat(context).getBean(CommandBus.class)
                        .isSameAs(context.getBean(CustomBusConfiguration.class).customCommandBus));
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerConfiguration {

        @Bean
        RecordingConsumers recordingConsumers() {
            return new RecordingConsumers();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateCommandConsumerConfiguration {

        @Bean
        SecondCommandConsumer secondCommandConsumer() {
            return new SecondCommandConsumer();
        }
    }

    static class SecondCommandConsumer {

        @io.github.siloverse.messaging.annotation.Consumer
        public void alsoConfirm(ConfirmOrder command) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBusConfiguration {

        private final CommandBus customCommandBus = provider -> {
        };

        @Bean
        CommandBus commandBus() {
            return customCommandBus;
        }
    }
}
