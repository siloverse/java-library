package io.github.siloverse.messaging.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.api.AsynchronousBus;
import io.github.siloverse.messaging.core.api.TransactionAwareAsynchronousBus;
import io.github.siloverse.messaging.core.dispatch.DefaultAsynchronousBus;
import io.github.siloverse.messaging.core.dispatch.DefaultTransactionAwareAsynchronousBus;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import io.github.siloverse.messaging.core.transport.OutboxWriter;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;
import io.github.siloverse.messaging.spring.outbox.JdbcOutboxWriter;
import io.github.siloverse.messaging.spring.outbox.OutboxRelay;
import io.github.siloverse.messaging.spring.outbox.OutboxRelaySettings;
import io.github.siloverse.messaging.spring.topology.TopologyDeclaration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The asynchronous chapter: buses, outbox, relay and lifecycle. Register ALONGSIDE
 * {@link MessagingConfiguration} -- and provide the application's side of the contract:
 * {@link MessageTransport}, {@link PayloadSerializer}, a composed {@link MessageNameRegistry},
 * {@link JdbcTemplate}, {@link ObjectMapper}, and (broker-specific) a
 * {@link TopologyDeclaration} bean. Sync-only applications simply do not register this class.
 */
@Configuration
public class AsyncMessagingConfiguration {

    @Bean
    AsynchronousBus asynchronousBus(
            MessageNameRegistry messageNameRegistry,
            MessageTransport messageTransport,
            PayloadSerializer payloadSerializer
    ) {
        return new DefaultAsynchronousBus(messageNameRegistry, messageTransport, payloadSerializer);
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
            MessageNameRegistry messageNameRegistry,
            OutboxWriter outboxWriter,
            PayloadSerializer payloadSerializer
    ) {
        return new DefaultTransactionAwareAsynchronousBus(messageNameRegistry, outboxWriter, payloadSerializer);
    }

    @Bean
    MessagingLifecycle messagingLifecycle(
            OutboxRelay outboxRelay,
            ObjectProvider<OutboxRelaySettings> settings,
            ObjectProvider<TopologyDeclaration> topologyDeclarations
    ) {
        return new MessagingLifecycle(
                outboxRelay,
                settings.getIfAvailable(() -> OutboxRelaySettings.DEFAULT),
                topologyDeclarations.orderedStream().toList()
        );
    }
}
