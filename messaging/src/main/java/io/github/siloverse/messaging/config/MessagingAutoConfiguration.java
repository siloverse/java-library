package io.github.siloverse.messaging.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.async.AsynchronousCommandBus;
import io.github.siloverse.messaging.async.AsynchronousEventBus;
import io.github.siloverse.messaging.async.MessagePoller;
import io.github.siloverse.messaging.async.MessageProcessor;
import io.github.siloverse.messaging.consumer.ConsumerIdStrategy;
import io.github.siloverse.messaging.consumer.ConsumerInvoker;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.consumer.ConsumerScanner;
import io.github.siloverse.messaging.consumer.DefaultConsumerIdStrategy;
import io.github.siloverse.messaging.dispatch.CommandDispatcher;
import io.github.siloverse.messaging.dispatch.EventDispatcher;
import io.github.siloverse.messaging.persistence.DeliveryClaimStrategy;
import io.github.siloverse.messaging.persistence.DurableMessageStore;
import io.github.siloverse.messaging.persistence.JpaDurableMessageStore;
import io.github.siloverse.messaging.persistence.SkipLockedDeliveryClaimStrategy;
import io.github.siloverse.messaging.persistence.repository.MessageDeliveryRepository;
import io.github.siloverse.messaging.persistence.repository.MessageRepository;
import io.github.siloverse.messaging.serialization.JacksonMessageSerializer;
import io.github.siloverse.messaging.serialization.MessageSerializer;
import io.github.siloverse.messaging.sync.SynchronousCommandBus;
import io.github.siloverse.messaging.sync.SynchronousEventBus;
import io.github.siloverse.messaging.transaction.TransactionAwareAsynchronousCommandBus;
import io.github.siloverse.messaging.transaction.TransactionAwareAsynchronousEventBus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;

/**
 * Wires the messaging library into a Spring Boot application.
 *
 * <p>The core half, consumer discovery and synchronous dispatch, needs nothing but an application
 * context. The durable half is added only when a {@link DataSource} and Spring Data JPA are present
 * and {@code messaging.async.enabled} is not turned off.
 *
 * <p>Every bean is declared {@code @ConditionalOnMissingBean}, so an application can replace any
 * part of the library by declaring its own bean.
 */
@AutoConfiguration(after = {
        HibernateJpaAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class,
        TaskExecutionAutoConfiguration.class,
        JacksonAutoConfiguration.class,
})
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ConsumerIdStrategy consumerIdStrategy() {
        return new DefaultConsumerIdStrategy();
    }

    @Bean
    @ConditionalOnMissingBean
    ConsumerScanner consumerScanner(ConsumerIdStrategy consumerIdStrategy) {
        return new ConsumerScanner(consumerIdStrategy);
    }

    @Bean
    @ConditionalOnMissingBean
    ConsumerRegistry consumerRegistry(ConsumerScanner consumerScanner,
                                      ConfigurableListableBeanFactory beanFactory) {
        return new ConsumerRegistry(consumerScanner, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    ConsumerInvoker consumerInvoker(ConfigurableListableBeanFactory beanFactory) {
        return new ConsumerInvoker(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    CommandDispatcher commandDispatcher(ConsumerRegistry registry, ConsumerInvoker invoker) {
        return new CommandDispatcher(registry, invoker);
    }

    @Bean
    @ConditionalOnMissingBean
    EventDispatcher eventDispatcher(ConsumerRegistry registry, ConsumerInvoker invoker) {
        return new EventDispatcher(registry, invoker);
    }

    @Bean
    @ConditionalOnMissingBean
    MessageSerializer messageSerializer(ObjectProvider<ObjectMapper> objectMapper) {
        ObjectMapper application = objectMapper.getIfUnique();
        // A copy keeps the application's modules while insulating message payloads from later
        // reconfiguration of the shared mapper.
        ObjectMapper mapper = application != null
                ? application.copy()
                : JsonMapper.builder().addModule(new JavaTimeModule()).build();
        return new JacksonMessageSerializer(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(CommandBus.class)
    CommandBus commandBus(MessagingProperties properties,
                          CommandDispatcher dispatcher,
                          ObjectProvider<DurableMessageStore> store) {
        return switch (properties.getCommand().getMode()) {
            case SYNC -> new SynchronousCommandBus(dispatcher);
            case ASYNC -> new AsynchronousCommandBus(requireStore(store, "messaging.command.mode"));
            case TRANSACTIONAL_ASYNC ->
                    new TransactionAwareAsynchronousCommandBus(requireStore(store, "messaging.command.mode"));
        };
    }

    @Bean
    @ConditionalOnMissingBean(EventBus.class)
    EventBus eventBus(MessagingProperties properties,
                      EventDispatcher dispatcher,
                      ObjectProvider<DurableMessageStore> store) {
        return switch (properties.getEvent().getMode()) {
            case SYNC -> new SynchronousEventBus(dispatcher);
            case ASYNC -> new AsynchronousEventBus(requireStore(store, "messaging.event.mode"));
            case TRANSACTIONAL_ASYNC ->
                    new TransactionAwareAsynchronousEventBus(requireStore(store, "messaging.event.mode"));
        };
    }

    private DurableMessageStore requireStore(ObjectProvider<DurableMessageStore> store, String property) {
        DurableMessageStore available = store.getIfAvailable();
        if (available == null) {
            throw new IllegalStateException(property + " selects a durable bus, but durable messaging "
                    + "is not configured. It needs a DataSource, Spring Data JPA on the classpath and "
                    + "messaging.async.enabled set to true.");
        }
        return available;
    }

    /**
     * Everything that needs a database: the message tables, the durable store, the poller and the
     * worker pool.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({JpaRepository.class, DataSource.class})
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnBooleanProperty(name = "messaging.async.enabled", matchIfMissing = true)
    @EnableJpaRepositories(basePackageClasses = MessageRepository.class)
    @Import(MessagingEntityRegistrar.class)
    static class DurableMessagingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DeliveryClaimStrategy deliveryClaimStrategy(ObjectProvider<JdbcOperations> jdbcOperations,
                                                    DataSource dataSource) {
            return new SkipLockedDeliveryClaimStrategy(
                    jdbcOperations.getIfAvailable(() -> new JdbcTemplate(dataSource)));
        }

        @Bean
        @ConditionalOnMissingBean
        DurableMessageStore durableMessageStore(MessageRepository messages,
                                                MessageDeliveryRepository deliveries,
                                                ConsumerRegistry registry,
                                                MessageSerializer serializer,
                                                ObjectProvider<Clock> clock) {
            return new JpaDurableMessageStore(messages, deliveries, registry, serializer, clock(clock));
        }

        @Bean
        @ConditionalOnMissingBean
        MessageProcessor messageProcessor(PlatformTransactionManager transactionManager,
                                          MessageDeliveryRepository deliveries,
                                          MessageRepository messages,
                                          MessageSerializer serializer,
                                          ConsumerRegistry registry,
                                          ConsumerInvoker invoker,
                                          MessagingProperties properties,
                                          ObjectProvider<Clock> clock) {
            return new MessageProcessor(
                    newTransactionTemplate(transactionManager),
                    deliveries,
                    messages,
                    serializer,
                    registry,
                    invoker,
                    clock(clock),
                    properties.getAsync().getMaxAttempts(),
                    properties.getAsync().getRetryDelay());
        }

        /**
         * The pool that runs claimed deliveries. Declared as a non default candidate so it never
         * becomes the application's {@code @Async} executor and does not suppress Spring Boot's own
         * {@code applicationTaskExecutor}.
         */
        @Bean(name = "messagingTaskExecutor", defaultCandidate = false)
        @ConditionalOnMissingBean(name = "messagingTaskExecutor")
        ThreadPoolTaskExecutor messagingTaskExecutor(MessagingProperties properties) {
            MessagingProperties.Async async = properties.getAsync();
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(async.getWorkerCount());
            executor.setMaxPoolSize(async.getWorkerCount());
            executor.setQueueCapacity(async.getQueueCapacity());
            executor.setThreadNamePrefix("messaging-worker-");
            executor.setWaitForTasksToCompleteOnShutdown(true);
            executor.setAwaitTerminationSeconds((int) async.getShutdownTimeout().toSeconds());
            return executor;
        }

        @Bean
        @ConditionalOnMissingBean
        MessagePoller messagePoller(DeliveryClaimStrategy claimStrategy,
                                    MessageDeliveryRepository deliveries,
                                    PlatformTransactionManager transactionManager,
                                    @Qualifier("messagingTaskExecutor") TaskExecutor taskExecutor,
                                    MessageProcessor processor,
                                    MessagingProperties properties,
                                    ObjectProvider<Clock> clock) {
            MessagingProperties.Async async = properties.getAsync();
            return new MessagePoller(
                    claimStrategy,
                    deliveries,
                    newTransactionTemplate(transactionManager),
                    taskExecutor,
                    processor,
                    clock(clock),
                    async.isPollerEnabled(),
                    async.getPollInterval(),
                    async.getInitialDelay(),
                    async.getBatchSize(),
                    async.getMaxAttempts(),
                    async.getRetryDelay(),
                    async.getLockTimeout());
        }

        @Bean
        @ConditionalOnBooleanProperty(name = "messaging.schema.initialize")
        @ConditionalOnMissingBean(name = "messagingSchemaInitializer")
        DataSourceScriptDatabaseInitializer messagingSchemaInitializer(DataSource dataSource,
                                                                       MessagingProperties properties) {
            DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
            settings.setSchemaLocations(List.of(properties.getSchema().getLocation()));
            settings.setMode(DatabaseInitializationMode.ALWAYS);
            settings.setContinueOnError(false);
            return new DataSourceScriptDatabaseInitializer(dataSource, settings);
        }

        /**
         * Claiming and processing always get a transaction of their own, whatever the caller was
         * doing. That matters because {@code pollOnce()} and {@code process()} may also be invoked
         * directly, and a claim that never commits would be invisible to the workers.
         */
        private static TransactionTemplate newTransactionTemplate(PlatformTransactionManager transactionManager) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            return template;
        }

        private static Clock clock(ObjectProvider<Clock> clock) {
            return clock.getIfUnique(Clock::systemUTC);
        }
    }
}
