package io.github.siloverse.messaging.spring.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OutboxRelayTest {

    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static JdbcTemplate jdbcTemplate;
    static JdbcOutboxWriter writer;   // the writer is its own fixture: relay and writer prove each other

    RecordingTransport transport;
    OutboxRelay relay;

    @BeforeAll
    static void startDatabaseAndApplySchema() {
        postgres.start();
        var dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute(shippedDdl());
        writer = new JdbcOutboxWriter(jdbcTemplate, new ObjectMapper());
    }

    @BeforeEach
    void setup() {
        jdbcTemplate.execute("TRUNCATE messaging_outbox");
        transport = new RecordingTransport();
        relay = new OutboxRelay(jdbcTemplate, transport, new ObjectMapper());
    }

    @Test
    void testPollPublishesPendingInOrderAndStampsEachRow() {
        var first = pendingEnvelope("order-silo.order-confirmed");
        var second = pendingEnvelope("order-silo.confirm-order");
        var third = pendingEnvelope("order-silo.order-confirmed");

        relay.pollOnce();

        assertThat(transport.sent).hasSize(3);
        assertThat(transport.sent).extracting(Envelope::messageId)
                .as("insertion order = id order = publish order")
                .containsExactly(first.messageId(), second.messageId(), third.messageId());

        var sentFirst = transport.sent.getFirst();
        assertThat(sentFirst.messageType()).isEqualTo(first.messageType());
        assertThat(sentFirst.payload()).as("payload bytes pass through sealed").isEqualTo(first.payload());
        assertThat(sentFirst.headers()).isEqualTo(first.headers());

        assertThat(pendingCount()).as("every published row is stamped").isZero();
    }

    @Test
    void testStampedRowsAreNotRepublished() {
        pendingEnvelope("order-silo.order-confirmed");

        relay.pollOnce();
        relay.pollOnce();

        assertThat(transport.sent).as("second poll sees no pending rows").hasSize(1);
    }

    @Test
    void testFailureStopsPollAndNextPollResumesAtFailedRow() {
        var first = pendingEnvelope("order-silo.order-confirmed");
        var second = pendingEnvelope("order-silo.confirm-order");
        var third = pendingEnvelope("order-silo.order-confirmed");

        transport.failOnSendNumber = 2;
        assertThatThrownBy(() -> relay.pollOnce()).isInstanceOf(MessagingException.class);

        assertThat(transport.sent).as("stop at first failure -- row 3 never attempted").hasSize(1);
        assertThat(pendingCount()).as("failed and unattempted rows stay pending").isEqualTo(2);

        // retry for free: broker recovers, next tick resumes exactly at the failed row
        transport.failOnSendNumber = -1;
        relay.pollOnce();

        assertThat(transport.sent).extracting(Envelope::messageId)
                .containsExactly(first.messageId(), second.messageId(), third.messageId());
        assertThat(pendingCount()).isZero();
    }

    private Envelope pendingEnvelope(String messageType) {
        var envelope = new Envelope(UUID.randomUUID(), messageType,
                Map.of("content-type", "test/fake"),
                ("payload-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        writer.append(envelope);
        return envelope;
    }

    private long pendingCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM messaging_outbox WHERE published_at IS NULL", Long.class);
        return count == null ? 0 : count;
    }

    private static String shippedDdl() {
        try (var ddl = OutboxRelayTest.class
                .getResourceAsStream("/io/github/siloverse/messaging/outbox-postgres.sql")) {
            assertThat(ddl).as("shipped DDL resource must exist on the classpath").isNotNull();
            return new String(ddl.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static class RecordingTransport implements MessageTransport {

        final List<Envelope> sent = new ArrayList<>();
        int failOnSendNumber = -1;

        @Override
        public void send(Envelope envelope) {
            if (sent.size() + 1 == failOnSendNumber) {
                throw new MessagingException("broker unavailable (test)");
            }
            sent.add(envelope);
        }
    }
}
