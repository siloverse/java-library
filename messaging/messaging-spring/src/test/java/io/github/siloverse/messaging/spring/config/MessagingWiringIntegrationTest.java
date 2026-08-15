package io.github.siloverse.messaging.spring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Connection;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.api.TransactionAwareAsynchronousBus;
import io.github.siloverse.messaging.core.consumer.Consumer;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import io.github.siloverse.messaging.core.transport.PayloadSerializer;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnectionSettings;
import io.github.siloverse.messaging.rabbitmq.connection.RabbitMqConnector;
import io.github.siloverse.messaging.rabbitmq.topology.RabbitMqTopologyDeclarer;
import io.github.siloverse.messaging.rabbitmq.listener.RabbitMqMessageListener;
import io.github.siloverse.messaging.rabbitmq.transport.RabbitMqMessageTransport;
import io.github.siloverse.messaging.spring.inbox.JdbcInbox;
import io.github.siloverse.messaging.spring.listener.MessageListener;
import io.github.siloverse.messaging.spring.outbox.OutboxRelaySettings;
import io.github.siloverse.messaging.spring.topology.TopologyDeclaration;
import io.github.siloverse.messaging.spring.serialization.JacksonPayloadDeserializer;
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
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
    void committedTransactionReachesTheConsumerMethodAsATypedEvent() throws Exception {
        var bus = context.getBean(TransactionAwareAsynchronousBus.class);
        var transactions = context.getBean(TransactionTemplate.class);
        var worker = context.getBean(OrderWorker.class);

        transactions.executeWithoutResult(status -> bus.publish(new OrderConfirmed(42)));

        // the WHOLE pipeline: tx -> outbox -> relay -> broker -> queue -> listener -> method
        var received = worker.received.poll(10, TimeUnit.SECONDS);
        assertThat(received)
                .as("the @Consumer method must receive the deserialized, typed event")
                .isEqualTo(new OrderConfirmed(42));

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
        assertThat(context.getBean(OrderWorker.class).received.poll(1, TimeUnit.SECONDS))
                .as("a rolled-back publish must never reach a consumer")
                .isNull();
    }

    @Test
    void duplicateWireDeliveryReachesTheDedupConsumerExactlyOnce() throws Exception {
        var transport = context.getBean(MessageTransport.class);
        var worker = context.getBean(OrderWorker.class);
        var objectMapper = context.getBean(ObjectMapper.class);

        // byte-identical duplicate with one messageId: what the relay's crash story produces
        var duplicateId = UUID.randomUUID();
        var payload = objectMapper.writeValueAsBytes(new OrderConfirmed(77));
        var headers = java.util.Map.of("content-type", "application/json");
        transport.send(new Envelope(duplicateId, "order-silo.order-confirmed", headers, payload));
        transport.send(new Envelope(duplicateId, "order-silo.order-confirmed", headers, payload));

        assertThat(worker.received.poll(10, TimeUnit.SECONDS))
                .as("the first delivery is processed")
                .isEqualTo(new OrderConfirmed(77));
        assertThat(worker.received.poll(2, TimeUnit.SECONDS))
                .as("the duplicate must be deduplicated by the JDBC inbox, not reprocessed")
                .isNull();

        var jdbcTemplate = context.getBean(JdbcTemplate.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messaging_inbox WHERE consumer_id = 'order-worker' AND message_id = ?",
                Long.class, duplicateId))
                .as("exactly one ledger entry for the pair")
                .isEqualTo(1L);
    }

    private static long outboxRowCount(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM messaging_outbox", Long.class);
    }

    // -- the application's side of the contract ------------------------------

    public record OrderConfirmed(int orderId) implements Event {
    }

    public static class OrderWorker {
        final BlockingQueue<OrderConfirmed> received = new LinkedBlockingQueue<>();

        @Consumer(id = "order-worker", dedup = true)
        void on(OrderConfirmed event) {
            received.add(event);
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
            jdbcTemplate.execute(shippedDdl("outbox-postgres.sql"));
            jdbcTemplate.execute(shippedDdl("inbox-postgres.sql"));
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
        MessageListener rabbitListener(
                RabbitMqConnectionSettings settings,
                io.github.siloverse.messaging.core.consumer.ConsumerRegistry consumers,
                MessageNameRegistry names,
                ObjectMapper objectMapper,
                io.github.siloverse.messaging.core.dispatch.MessageDispatcher dispatcher,
                JdbcTemplate jdbcTemplate,
                TransactionTemplate transactionTemplate
        ) {
            // the consume connection is the listening machinery's internal detail, not a
            // context bean: opened on start, closed on stop, never shared with publishing
            return new MessageListener() {
                private Connection consumeConnection;
                private RabbitMqMessageListener listener;

                @Override
                public void start() {
                    consumeConnection = RabbitMqConnector.connect(settings);
                    listener = new RabbitMqMessageListener(consumeConnection, "billing-silo", consumers, names,
                            new JacksonPayloadDeserializer(objectMapper), dispatcher,
                            new JdbcInbox(jdbcTemplate, transactionTemplate));
                    listener.start();
                }

                @Override
                public void stop() {
                    if (listener != null) {
                        listener.stop();
                    }
                    try {
                        if (consumeConnection != null) {
                            consumeConnection.close();
                        }
                    } catch (Exception ignored) {
                    }
                }
            };
        }

        @Bean
        OrderWorker orderWorker() {
            return new OrderWorker();
        }

        private static String shippedDdl(String resource) {
            try (var ddl = MessagingWiringIntegrationTest.class.getResourceAsStream(
                    "/io/github/siloverse/messaging/" + resource)) {
                assertThat(ddl).as("shipped DDL resource must exist on the classpath").isNotNull();
                return new String(ddl.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
