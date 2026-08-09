package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.async.MessagePoller;
import io.github.siloverse.messaging.fixture.RecordingConsumers;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import io.github.siloverse.messaging.persistence.entity.StoredMessage;
import io.github.siloverse.messaging.persistence.repository.MessageDeliveryRepository;
import io.github.siloverse.messaging.persistence.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Base for the durable messaging tests.
 *
 * <p>Both buses run in {@code TRANSACTIONAL_ASYNC} mode and the background poller is switched off,
 * so each test decides exactly when a poll cycle happens. Every subclass shares this configuration
 * and therefore a single cached application context and a single PostgreSQL container.
 */
@SpringBootTest(
        classes = TestMessagingApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql",
                "messaging.schema.initialize=true",
                "messaging.command.mode=transactional_async",
                "messaging.event.mode=transactional_async",
                "messaging.async.poller-enabled=false",
                "messaging.async.max-attempts=3",
                "messaging.async.retry-delay=30s",
                "messaging.async.lock-timeout=5m"
        })
abstract class AbstractDurableMessagingTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        // Started once for the whole suite and reused; Ryuk removes it when the JVM exits.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MessageRepository messages;

    @Autowired
    protected MessageDeliveryRepository deliveries;

    @Autowired
    protected MessagePoller poller;

    @Autowired
    protected RecordingConsumers consumers;

    @Autowired
    protected TestOrderService orderService;

    @Autowired
    protected TestOrderRepository orders;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE message_deliveries, messages, test_orders");
        consumers.reset();
    }

    /**
     * Runs poll cycles until the workers have finished, or fails after a few seconds.
     *
     * @return the number of deliveries handed to the workers by the last cycle
     */
    protected int pollAndAwaitCompletion() {
        int submitted = poller.pollOnce();
        awaitUntil(() -> deliveries.countByStatus(DeliveryStatus.PROCESSING) == 0);
        return submitted;
    }

    protected void awaitUntil(BooleanSupplier condition) {
        await().atMost(10, TimeUnit.SECONDS).pollInterval(20, TimeUnit.MILLISECONDS).until(condition::getAsBoolean);
    }

    protected StoredMessage singleStoredMessage() {
        List<StoredMessage> stored = messages.findAll();
        assertThat(stored).hasSize(1);
        return stored.getFirst();
    }

    protected List<MessageDelivery> deliveriesOf(StoredMessage message) {
        return deliveries.findAllByMessageId(message.getId());
    }

    protected MessageDelivery reload(MessageDelivery delivery) {
        return deliveries.findById(delivery.getId()).orElseThrow();
    }

    /**
     * Makes a delivery eligible again without waiting for its retry delay.
     *
     * @param deliveryId the delivery to move
     */
    protected void makeAvailableNow(UUID deliveryId) {
        jdbc.update("UPDATE message_deliveries SET available_at = ? WHERE id = ?",
                Instant.now().atOffset(ZoneOffset.UTC), deliveryId);
    }
}
