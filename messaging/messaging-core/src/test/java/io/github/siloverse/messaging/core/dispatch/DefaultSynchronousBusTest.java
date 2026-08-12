package io.github.siloverse.messaging.core.dispatch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.error.ConsumerInvocationException;
import io.github.siloverse.messaging.core.fixtures.ConfirmOrder;
import io.github.siloverse.messaging.core.fixtures.OrderConfirmed;
import io.github.siloverse.messaging.core.fixtures.QuoteShipping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DefaultSynchronousBusTest {

    ConsumerRegistry registry;
    Handlers handlers;
    DefaultSynchronousBus bus;

    @BeforeEach
    void setUp() {
        registry = new ConsumerRegistry();
        handlers = new Handlers();
        bus = new DefaultSynchronousBus(registry, new MessageDispatcher());
    }

    private ConsumerDefinition def(String id, Class<?> msg, String methodName) throws Exception {
        var m = Handlers.class.getDeclaredMethod(methodName, msg);
        return new ConsumerDefinition(id, msg, handlers, m, -1);
    }

    @Test
    void publishInvokesBothConsumersInOrder() throws Exception {
        registry.register(def("a", OrderConfirmed.class, "consumeA"));
        registry.register(def("b", OrderConfirmed.class, "consumeB"));

        var event = new OrderConfirmed(UUID.randomUUID().toString());
        bus.publish(event);

        assertThat(handlers.received).containsExactly(event, event);
    }

    @Test
    void publishWithNoConsumersDoesNothing() {
        var event = new OrderConfirmed(UUID.randomUUID().toString());

        assertThatCode(() -> bus.publish(event)).doesNotThrowAnyException();
        assertThat(handlers.received).isEmpty();
    }

    @Test
    void sendInvokesConsumerWithSameCommandInstance() throws Exception {
        registry.register(def("handler", ConfirmOrder.class, "handle"));
        var command = new ConfirmOrder(UUID.randomUUID().toString());

        bus.send(command);

        assertThat(handlers.received).singleElement().isSameAs(command);
    }

    @Test
    void publishRethrowsRuntimeExceptionFromConsumer() throws Exception {
        registry.register(def("failing", OrderConfirmed.class, "failing"));
        var event = new OrderConfirmed(UUID.randomUUID().toString());

        assertThatThrownBy(() -> bus.publish(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }

    @Test
    void publishWrapsCheckedExceptionFromConsumer() throws Exception {
        registry.register(def("failing-checked", OrderConfirmed.class, "failingChecked"));
        var event = new OrderConfirmed(UUID.randomUUID().toString());

        assertThatThrownBy(() -> bus.publish(event))
                .isInstanceOf(ConsumerInvocationException.class)
                .hasCauseInstanceOf(Exception.class)
                .hasRootCauseMessage("checked boom");
    }

    /** Recording fixture — no Mockito, just a list. */
    static class Handlers {
        final List<Object> received = new ArrayList<>();

        public void consumeA(OrderConfirmed e) {
            received.add(e);
        }

        public void consumeB(OrderConfirmed e) {
            received.add(e);
        }

        public void handle(ConfirmOrder c) {
            received.add(c);
        }

        public BigDecimal quote(QuoteShipping r) {
            return new BigDecimal("9.99");
        }

        public void failing(OrderConfirmed e) {
            throw new IllegalStateException("boom");
        }

        public void failingChecked(OrderConfirmed e) throws Exception {
            throw new Exception("checked boom");
        }
    }
}
