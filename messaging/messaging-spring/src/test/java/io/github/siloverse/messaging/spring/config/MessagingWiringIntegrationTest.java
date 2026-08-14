package io.github.siloverse.messaging.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.api.TransactionAwareAsynchronousBus;
import io.github.siloverse.messaging.core.consumer.Consumer;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnectionSettings;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnector;
import io.github.siloverse.messaging.rabbitmq.topology.RabbitMqTopologyDeclarer;
import io.github.siloverse.messaging.rabbitmq.transport.RabbitMqMessageTransport;
import io.github.siloverse.messaging.spring.outbox.OutboxRelaySettings;
import io.github.siloverse.messaging.spring.topology.TopologyDeclaration;
import io.github.siloverse.messaging.spring.serialization.JacksonPayloadSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * The whole async pipeline, wired by Spring, against real infrastructure: committed transaction -> outbox row -> relay
 * -> broker -> consumer queue.
 */
class MessagingWiringIntegrationTest {

    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static AnnotationConfigApplicationContext context;

    @BeforeAll
    static void startInfrastructureAndContext() {
        postgres.start();
        rabbit.start();

        context = new AnnotationConfigApplicationContext();
        context.register(MessagingConfiguration.class, AsyncMessagingConfiguration.class, BillingSiloApp.class);
        context.refresh();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
        rabbit.stop();
        postgres.stop();
    }

    @Test
    void committedTransactionDeliversTheEventToTheConsumerQueue() throws Exception {
        var bus = context.getBean(TransactionAwareAsynchronousBus.class);
        var transactions = context.getBean(TransactionTemplate.class);

        transactions.executeWithoutResult(status -> bus.publish(new OrderConfirmed(42)));

        // no listener exists yet, so the delivered message WAITS in the queue for us to read
        var delivered = awaitDelivery("billing-silo-order-worker", Duration.ofSeconds(10));

        assertThat(new String(delivered.getBody(), StandardCharsets.UTF_8)).contains("42");
        assertThat(delivered.getProps().getMessageId()).isNotBlank();
        assertThat(delivered.getProps().getDeliveryMode()).as("the pipeline must preserve persistence end to end")
                .isEqualTo(2);

        // the relay's half of the proof: the shipped row is stamped as published
        var jdbcTemplate = context.getBean(JdbcTemplate.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM messaging_outbox WHERE published_at IS NOT NULL",
                Long.class)).isEqualTo(1L);
    }

    @Test
    void rolledBackTransactionDeliversNothing() throws Exception {
        var bus = context.getBean(TransactionAwareAsynchronousBus.class);
        var transactions = context.getBean(TransactionTemplate.class);
        var jdbcTemplate = context.getBean(JdbcTemplate.class);
        long rowsBefore = outboxRowCount(jdbcTemplate);

        transactions.executeWithoutResult(status -> {
            bus.publish(new OrderConfirmed(1337));
            status.setRollbackOnly();
        });

        // proving absence needs a deadline, not a poll-until: give the relay several ticks
        Thread.sleep(500);

        assertThat(outboxRowCount(jdbcTemplate))
                .as("the outbox row must roll back with the business transaction")
                .isEqualTo(rowsBefore);
        var connection = context.getBean(Connection.class);
        try (var channel = connection.createChannel()) {
            assertThat(channel.basicGet("billing-silo-order-worker", true))
                    .as("a rolled-back publish must never reach the broker")
                    .isNull();
        }
    }

    private static long outboxRowCount(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM messaging_outbox", Long.class);
    }

    private static com.rabbitmq.client.GetResponse awaitDelivery(String queue, Duration deadline) throws Exception {
        var connection = context.getBean(Connection.class);
        var end = Instant.now().plus(deadline);
        try (var channel = connection.createChannel()) {
            while (Instant.now().isBefore(end)) {
                var response = channel.basicGet(queue, true);
                if (response != null) {
                    return response;
                }
                Thread.sleep(50);
            }
        }
        throw new AssertionError("no message arrived in queue '" + queue + "' within " + deadline);
    }

    // -- the application's side of the contract ------------------------------

    public record OrderConfirmed(int orderId) implements Event {
    }

    public static class OrderWorker {
        @Consumer(id = "order-worker")
        void on(OrderConfirmed event) {
        }
    }

    @Configuration
    static class BillingSiloApp {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            var jdbcTemplate = new JdbcTemplate(dataSource);
            jdbcTemplate.execute(shippedDdl());
            return jdbcTemplate;
        }

        @Bean
        TransactionTemplate transactionTemplate(DataSource dataSource) {
            return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RabbitMqConnectionSettings rabbitSettings() {
            return new RabbitMqConnectionSettings(rabbit.getHost(), rabbit.getAmqpPort(), rabbit.getAdminUsername(),
                    rabbit.getAdminPassword());
        }

        @Bean(destroyMethod = "close")
        Connection rabbitConnection(RabbitMqConnectionSettings settings) {
            return RabbitMqConnector.connect(settings);
        }

        @Bean
        MessageTransport messageTransport(Connection connection) {
            return new RabbitMqMessageTransport(connection);
        }

        @Bean
        PayloadSerializer payloadSerializer(ObjectMapper objectMapper) {
            return new JacksonPayloadSerializer(objectMapper);
        }

        @Bean
        MessageNameRegistry messageNames() {
            return MessageNameRegistry.builder().register(OrderConfirmed.class, "order-silo.order-confirmed").freeze();
        }

        @Bean
        TopologyDeclaration rabbitTopology(
                Connection connection,
                io.github.siloverse.messaging.core.consumer.ConsumerRegistry consumers,
                MessageNameRegistry names
        ) {
            var declarer = new RabbitMqTopologyDeclarer(connection);
            return () -> {
                declarer.declarePublisherTopology(names);
                declarer.declareConsumerTopology("billing-silo", consumers, names);
            };
        }

        @Bean
        OutboxRelaySettings outboxRelaySettings() {
            return new OutboxRelaySettings(Duration.ofMillis(100));
        }

        @Bean
        OrderWorker orderWorker() {
            return new OrderWorker();
        }

        private static String shippedDdl() {
            try (var ddl = MessagingWiringIntegrationTest.class.getResourceAsStream(
                    "/io/github/siloverse/messaging/outbox-postgres.sql")) {
                assertThat(ddl).as("shipped DDL resource must exist on the classpath").isNotNull();
                return new String(ddl.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
