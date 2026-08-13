package io.github.siloverse.messaging.spring.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.transport.Envelope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JdbcOutboxWriterTest {

    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static JdbcTemplate jdbcTemplate;
    static TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeAll
    static void startDatabaseAndApplySchema() {
        postgres.start();

        var dataSource =
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        // apply the exact DDL we ship in the jar -- if this file rots, this line fails
        jdbcTemplate.execute(shippedDdl());
    }

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.execute("TRUNCATE messaging_outbox");
    }

    @Test
    void scaffoldTableExistsAndIsEmpty() {
        assertThat(rowCount()).isZero();
    }

    @Test
    void testCommittedTransactionKeepsTheRow() {
        var writer = new JdbcOutboxWriter(jdbcTemplate, objectMapper);
        var envelope =
                new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", Map.of("content-type", "test/fake"),
                        "payload-bytes".getBytes(StandardCharsets.UTF_8));

        transactionTemplate.executeWithoutResult(tx -> writer.append(envelope));

        var row = jdbcTemplate.queryForMap("SELECT * FROM messaging_outbox");
        assertThat(row.get("message_id")).isEqualTo(envelope.messageId());
        assertThat(row.get("message_type")).isEqualTo("order-silo.order-confirmed");
        assertThat((byte[]) row.get("payload")).isEqualTo(envelope.payload());
        assertThat(readHeaders(row)).isEqualTo(envelope.headers());
        assertThat(row.get("published_at")).as("NULL published_at means pending for the relay").isNull();
        assertThat(row.get("id")).as("id is the database's column").isNotNull();
        assertThat(row.get("created_at")).as("created_at is the database's column").isNotNull();
    }

    private Map<String, String> readHeaders(Map<String, Object> row) {
        try {
            return objectMapper.readValue(
                    String.valueOf(row.get("headers")),
                    new TypeReference<>() {
                    }
            );
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void testRolledBackTransactionLeavesNoRow() {
        var writer = new JdbcOutboxWriter(jdbcTemplate, new ObjectMapper());
        var envelope =
                new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", Map.of("content-type", "test/fake"),
                        "payload-bytes".getBytes(StandardCharsets.UTF_8));

        transactionTemplate.executeWithoutResult(tx -> {
            writer.append(envelope);
            tx.setRollbackOnly();
        });

        // the reason this library exists: the message dies with the transaction
        assertThat(rowCount()).isZero();
    }

    @Test
    void testAppendWithoutTransactionStillWrites() {
        var writer = new JdbcOutboxWriter(jdbcTemplate, new ObjectMapper());
        var envelope =
                new Envelope(UUID.randomUUID(), "order-silo.order-confirmed", Map.of("content-type", "test/fake"),
                        "payload-bytes".getBytes(StandardCharsets.UTF_8));

        writer.append(envelope);

        // pins the javadoc: no transaction = direct write, no rollback safety
        assertThat(rowCount()).isEqualTo(1);
    }

    private long rowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM messaging_outbox", Long.class);
        return count == null ? 0 : count;
    }

    private static String shippedDdl() {
        try (var ddl = JdbcOutboxWriterTest.class.getResourceAsStream(
                "/io/github/siloverse/messaging/outbox-postgres.sql")) {
            assertThat(ddl).as("shipped DDL resource must exist on the classpath").isNotNull();
            return new String(ddl.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
