package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One event, two consumers: a failure in one must not affect the other.
 */
class ConsumerIsolationTest extends AbstractDurableMessagingTest {

    @Autowired
    private ConsumerRegistry registry;

    @Test
    void aFailingConsumerDoesNotAffectTheSuccessfulOne() {
        UUID orderId = UUID.randomUUID();
        consumers.failAnalyticsFor(orderId);

        orderService.publishInTransaction(new OrderConfirmed(orderId));
        pollAndAwaitCompletion();

        Map<String, MessageDelivery> byConsumer = deliveriesByConsumerMethod();
        assertThat(byConsumer.get("sendEmail")).satisfies(email -> {
            assertThat(email.getStatus()).isEqualTo(DeliveryStatus.PROCESSED);
            assertThat(email.getLastError()).isNull();
        });
        assertThat(byConsumer.get("updateAnalytics")).satisfies(analytics -> {
            assertThat(analytics.getStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(analytics.getAttempts()).isEqualTo(1);
            assertThat(analytics.getLastError()).contains("updateAnalytics was told to fail");
        });
    }

    @Test
    void onlyTheFailingConsumerIsRetried() {
        UUID orderId = UUID.randomUUID();
        consumers.failAnalyticsFor(orderId);
        orderService.publishInTransaction(new OrderConfirmed(orderId));
        pollAndAwaitCompletion();

        makeAvailableNow(deliveriesByConsumerMethod().get("updateAnalytics").getId());
        assertThat(pollAndAwaitCompletion()).isEqualTo(1);

        assertThat(consumers.emails()).hasSize(1);
        assertThat(consumers.analytics()).hasSize(2);
    }

    private Map<String, MessageDelivery> deliveriesByConsumerMethod() {
        return deliveriesOf(singleStoredMessage()).stream()
                .map(this::reload)
                .collect(Collectors.toMap(
                        delivery -> registry.findById(delivery.getConsumerId()).orElseThrow()
                                .method().getName(),
                        Function.identity()));
    }
}
