package io.github.siloverse.messaging.api;

import io.github.siloverse.messaging.consumer.ConsumerInvoker;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.dispatch.EventDispatcher;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.sync.SynchronousEventBus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MessageProviderTest {

    @Test
    void ofReturnsTheProvidedMessage() {
        OrderConfirmed event = new OrderConfirmed(UUID.randomUUID());

        assertThat(MessageProvider.of(event).provide()).isSameAs(event);
    }

    @Test
    void lambdaProviderWorks() {
        UUID orderId = UUID.randomUUID();

        MessageProvider<OrderConfirmed> provider = () -> new OrderConfirmed(orderId);

        assertThat(provider.provide()).isEqualTo(new OrderConfirmed(orderId));
    }

    @Test
    void providerIsNotEvaluatedBeforePublishing() {
        AtomicInteger evaluations = new AtomicInteger();
        MessageProvider<OrderConfirmed> provider = () -> {
            evaluations.incrementAndGet();
            return new OrderConfirmed(UUID.randomUUID());
        };

        assertThat(evaluations).hasValue(0);

        newEventBus().publish(provider);

        assertThat(evaluations).hasValue(1);
    }

    @Test
    void providerIsEvaluatedOncePerPublish() {
        AtomicInteger evaluations = new AtomicInteger();
        MessageProvider<OrderConfirmed> provider = () -> {
            evaluations.incrementAndGet();
            return new OrderConfirmed(UUID.randomUUID());
        };
        SynchronousEventBus bus = newEventBus();

        bus.publish(provider);
        bus.publish(provider);

        assertThat(evaluations).hasValue(2);
    }

    private SynchronousEventBus newEventBus() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ConsumerRegistry registry = new ConsumerRegistry(null, beanFactory);
        return new SynchronousEventBus(new EventDispatcher(registry, new ConsumerInvoker(beanFactory)));
    }
}
