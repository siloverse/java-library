package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.api.CommandBus;
import io.github.siloverse.messaging.api.EventBus;
import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.fixture.RecordingConsumers;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.repository.MessageDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End to end check of the background poller in {@code ASYNC} mode, where the bus opens its own
 * transaction and callers need no transaction of their own.
 */
@SpringBootTest(
        classes = TestMessagingApplication.class,
        properties = {
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:test-schema.sql",
                "messaging.schema.initialize=true",
                "messaging.command.mode=async",
                "messaging.event.mode=async",
                "messaging.async.poller-enabled=true",
                "messaging.async.initial-delay=0s",
                "messaging.async.poll-interval=50ms"
        })
class BackgroundPollerTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AbstractDurableMessagingTest.POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", AbstractDurableMessagingTest.POSTGRES::getUsername);
        registry.add("spring.datasource.password", AbstractDurableMessagingTest.POSTGRES::getPassword);
    }

    @Autowired
    private EventBus eventBus;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingConsumers consumers;

    @Autowired
    private MessageDeliveryRepository deliveries;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE message_deliveries, messages, test_orders");
        consumers.reset();
    }

    @Test
    void anEventPublishedWithoutATransactionIsDeliveredInTheBackground() {
        UUID orderId = UUID.randomUUID();

        eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId)));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(consumers.emails()).containsExactly(new OrderConfirmed(orderId));
            assertThat(consumers.analytics()).containsExactly(new OrderConfirmed(orderId));
            assertThat(deliveries.countByStatus(DeliveryStatus.PROCESSED)).isEqualTo(2);
        });
    }

    @Test
    void aCommandSentWithoutATransactionIsDeliveredInTheBackground() {
        UUID orderId = UUID.randomUUID();

        commandBus.send(MessageProvider.of(new ConfirmOrder(orderId)));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(consumers.commands()).containsExactly(new ConfirmOrder(orderId));
            assertThat(deliveries.countByStatus(DeliveryStatus.PROCESSED)).isEqualTo(1);
        });
    }
}
