package io.github.siloverse.messaging.spring.inbox;

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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * The dedup ledger: UNIQUE on (consumer_id, message_id) IS the mechanism, and the ledger
 * entry commits or rolls back WITH the business invocation.
 */
class JdbcInboxTest {

    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    static JdbcTemplate jdbcTemplate;
    static TransactionTemplate transactionTemplate;

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
        jdbcTemplate.execute("TRUNCATE messaging_inbox");
    }

    @Test
    void firstDeliveryRunsTheInvocationAndRecordsIt() {
        var inbox = new JdbcInbox(jdbcTemplate, transactionTemplate);
        var invocations = new AtomicInteger();

        boolean processed = inbox.processOnce("order-worker", UUID.randomUUID(), invocations::incrementAndGet);

        assertThat(processed).isTrue();
        assertThat(invocations.get()).isEqualTo(1);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void duplicateIsSkippedWithoutInvokingTheBusinessLogic() {
        var inbox = new JdbcInbox(jdbcTemplate, transactionTemplate);
        var invocations = new AtomicInteger();
        var messageId = UUID.randomUUID();

        inbox.processOnce("order-worker", messageId, invocations::incrementAndGet);
        boolean processedAgain = inbox.processOnce("order-worker", messageId, invocations::incrementAndGet);

        assertThat(processedAgain).as("the duplicate must be reported, not reprocessed").isFalse();
        assertThat(invocations.get()).as("business logic must run exactly once").isEqualTo(1);
    }

    @Test
    void dedupIsPerConsumerNotPerMessage() {
        var inbox = new JdbcInbox(jdbcTemplate, transactionTemplate);
        var messageId = UUID.randomUUID();

        inbox.processOnce("order-worker", messageId, () -> {
        });
        boolean otherConsumer = inbox.processOnce("invoice-worker", messageId, () -> {
        });

        assertThat(otherConsumer)
                .as("two consumers legitimately each process the same event once -- the key is"
                        + " (consumer_id, message_id)")
                .isTrue();
    }

    @Test
    void failedInvocationRollsBackTheLedgerEntrySoRetryRunsAgain() {
        var inbox = new JdbcInbox(jdbcTemplate, transactionTemplate);
        var invocations = new AtomicInteger();
        var messageId = UUID.randomUUID();

        assertThatThrownBy(() -> inbox.processOnce("order-worker", messageId, () -> {
            invocations.incrementAndGet();
            throw new IllegalStateException("business failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowCount())
                .as("ledger entry and business effects live or die together -- a failed run"
                        + " leaves no trace, so the redelivery passes the gate")
                .isZero();

        boolean retried = inbox.processOnce("order-worker", messageId, invocations::incrementAndGet);
        assertThat(retried).isTrue();
        assertThat(invocations.get()).isEqualTo(2);
    }

    private static long rowCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM messaging_inbox", Long.class);
    }

    private static String shippedDdl() {
        try (var ddl = JdbcInboxTest.class.getResourceAsStream(
                "/io/github/siloverse/messaging/inbox-postgres.sql")) {
            assertThat(ddl).as("shipped DDL resource must exist on the classpath").isNotNull();
            return new String(ddl.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
