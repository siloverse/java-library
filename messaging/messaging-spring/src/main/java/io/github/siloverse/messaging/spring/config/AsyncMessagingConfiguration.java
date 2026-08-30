package io.github.siloverse.messaging.spring.config;

import io.github.siloverse.messaging.core.api.AsynchronousBus;
import io.github.siloverse.messaging.core.api.TransactionAwareAsynchronousBus;
import io.github.siloverse.messaging.core.dispatch.DefaultAsynchronousBus;
import io.github.siloverse.messaging.core.dispatch.DefaultTransactionAwareAsynchronousBus;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.EnvelopeFactory;
import io.github.siloverse.messaging.core.transport.EnvelopeHeadersContributor;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import io.github.siloverse.messaging.core.transport.OutboxWriter;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;
import io.github.siloverse.messaging.spring.listener.MessageListener;
import io.github.siloverse.messaging.spring.outbox.JdbcOutboxWriter;
import io.github.siloverse.messaging.spring.outbox.OutboxRelay;
import io.github.siloverse.messaging.spring.outbox.OutboxRelaySettings;
import io.github.siloverse.messaging.spring.topology.TopologyDeclaration;
import io.github.siloverse.messaging.spring.tracing.TracingHeadersContributor;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * The asynchronous chapter: buses, outbox, relay and lifecycle. Register ALONGSIDE {@link MessagingConfiguration} --
 * and provide the application's side of the contract: {@link MessageTransport}, {@link PayloadSerializer}, a composed
 * {@link MessageNameRegistry}, {@link JdbcTemplate}, {@link ObjectMapper}, and (broker-specific) a
 * {@link TopologyDeclaration} bean. Sync-only applications simply do not register this class.
 */
@Configuration
public class AsyncMessagingConfiguration {

    // decorate with tracing when the context can trace, contribute nothing when it can't
    // (a context without an observability stack still publishes valid envelopes)
    @Bean
    EnvelopeHeadersContributor envelopeHeadersContributor(
            ObjectProvider<Tracer> tracer,
            ObjectProvider<Propagator> propagator
    ) {
        Tracer availableTracer = tracer.getIfAvailable();
        Propagator availablePropagator = propagator.getIfAvailable();
        if (availableTracer == null || availablePropagator == null) {
            return headers -> {
            };
        }
        return new TracingHeadersContributor(availableTracer, availablePropagator);
    }

    @Bean
    EnvelopeFactory envelopFactory(
            MessageNameRegistry messageNameRegistry,
            EnvelopeHeadersContributor envelopeHeadersContributor,
            PayloadSerializer payloadSerializer
    ) {
        return new EnvelopeFactory(messageNameRegistry, envelopeHeadersContributor, payloadSerializer);
    }

    @Bean
    AsynchronousBus asynchronousBus(
            EnvelopeFactory envelopeFactory,
            MessageTransport messageTransport
    ) {
        return new DefaultAsynchronousBus(envelopeFactory, messageTransport);
    }

    @Bean
    OutboxWriter outboxWriter(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        return new JdbcOutboxWriter(jdbcTemplate, objectMapper);
    }

    @Bean
    OutboxRelay outboxRelay(
            JdbcTemplate jdbcTemplate,
            MessageTransport messageTransport,
            ObjectMapper objectMapper
    ) {
        return new OutboxRelay(jdbcTemplate, messageTransport, objectMapper);
    }

    @Bean
    TransactionAwareAsynchronousBus transactionAwareAsynchronousBus(
            EnvelopeFactory envelopeFactory,
            OutboxWriter outboxWriter
    ) {
        return new DefaultTransactionAwareAsynchronousBus(envelopeFactory, outboxWriter);
    }

    @Bean
    MessagingLifecycle messagingLifecycle(
            OutboxRelay outboxRelay,
            ObjectProvider<OutboxRelaySettings> settings,
            ObjectProvider<TopologyDeclaration> topologyDeclarations,
            ObjectProvider<MessageListener> messageListeners
    ) {
        return new MessagingLifecycle(
                outboxRelay,
                settings.getIfAvailable(() -> OutboxRelaySettings.DEFAULT),
                topologyDeclarations.orderedStream().toList(),
                messageListeners.orderedStream().toList()
        );
    }
}
