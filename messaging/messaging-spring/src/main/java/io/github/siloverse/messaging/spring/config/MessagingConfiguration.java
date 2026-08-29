package io.github.siloverse.messaging.spring.config;

import io.github.siloverse.messaging.core.consumer.ConsumerMethodScanner;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.dispatch.DefaultMessageDispatcher;
import io.github.siloverse.messaging.core.dispatch.DefaultSynchronousBus;
import io.github.siloverse.messaging.core.dispatch.MessageDispatcher;
import io.github.siloverse.messaging.core.api.SynchronousBus;
import io.github.siloverse.messaging.spring.consumer.ConsumerScanningInitializer;
import io.github.siloverse.messaging.spring.consumer.SpringConsumerScanner;
import io.github.siloverse.messaging.spring.tracing.TracingMessageDispatcher;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * The synchronous chapter: consumer scanning, the registry lifecycle and the synchronous bus. Sufficient on its own for
 * a sync-only application; register {@link AsyncMessagingConfiguration} additionally for the outbox/broker machinery.
 */
@Configuration
public class MessagingConfiguration {

    // static: these are (or feed) a BeanFactoryPostProcessor and get created very
    // early; static @Bean methods avoid dragging the config class into premature
    // initialization and silence the "not eligible for post-processing" warning.
    @Bean
    static ConsumerRegistry consumerRegistry() {
        return new ConsumerRegistry();
    }

    @Bean
    static ConsumerMethodScanner consumerMethodScanner() {
        return new ConsumerMethodScanner();
    }

    @Bean
    static SpringConsumerScanner springConsumerScanner(ConsumerMethodScanner consumerMethodScanner) {
        return new SpringConsumerScanner(consumerMethodScanner);
    }

    @Bean
    static ConsumerScanningInitializer consumerScanningInitializer(SpringConsumerScanner springConsumerScanner,
            ConsumerRegistry consumerRegistry) {
        return new ConsumerScanningInitializer(springConsumerScanner, consumerRegistry);
    }

    @Bean
    MessageDispatcher messageDispatcher(
            ObjectMapper objectMapper,
            Tracer tracer,
            Propagator propagator
    ) {
        return new TracingMessageDispatcher(
                objectMapper,
                tracer,
                propagator,
                new DefaultMessageDispatcher()
        );
    }

    @Bean
    SynchronousBus synchronousBus(ConsumerRegistry registry, MessageDispatcher dispatcher) {
        return new DefaultSynchronousBus(registry, dispatcher);
    }
}
