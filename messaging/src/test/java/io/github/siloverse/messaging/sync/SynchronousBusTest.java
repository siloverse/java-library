package io.github.siloverse.messaging.sync;

import io.github.siloverse.messaging.api.MessageProvider;
import io.github.siloverse.messaging.consumer.ConsumerInvoker;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.consumer.ConsumerScanner;
import io.github.siloverse.messaging.consumer.DefaultConsumerIdStrategy;
import io.github.siloverse.messaging.dispatch.CommandDispatcher;
import io.github.siloverse.messaging.dispatch.EventDispatcher;
import io.github.siloverse.messaging.exception.NoConsumerForCommandException;
import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.fixture.OrderCancelled;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.fixture.RecordingConsumers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Synchronous dispatch, exercised against a real application context but without a database.
 */
class SynchronousBusTest {

    private AnnotationConfigApplicationContext context;
    private RecordingConsumers consumers;
    private SynchronousCommandBus commandBus;
    private SynchronousEventBus eventBus;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(RecordingConsumers.class);
        consumers = context.getBean(RecordingConsumers.class);

        ConsumerScanner scanner = new ConsumerScanner(new DefaultConsumerIdStrategy());
        ConsumerRegistry registry = new ConsumerRegistry(scanner, context.getBeanFactory());
        registry.afterSingletonsInstantiated();
        ConsumerInvoker invoker = new ConsumerInvoker(context.getBeanFactory());

        commandBus = new SynchronousCommandBus(new CommandDispatcher(registry, invoker));
        eventBus = new SynchronousEventBus(new EventDispatcher(registry, invoker));
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void commandReachesExactlyOneConsumer() {
        UUID orderId = UUID.randomUUID();

        commandBus.send(MessageProvider.of(new ConfirmOrder(orderId)));

        assertThat(consumers.commands()).containsExactly(new ConfirmOrder(orderId));
    }

    @Test
    void commandDispatchIsSynchronous() {
        String caller = Thread.currentThread().getName();

        commandBus.send(MessageProvider.of(new ConfirmOrder(UUID.randomUUID())));

        assertThat(consumers.commands()).hasSize(1);
        assertThat(Thread.currentThread().getName()).isEqualTo(caller);
    }

    @Test
    void commandFailurePropagatesToCaller() {
        UUID orderId = UUID.randomUUID();
        consumers.failCommandFor(orderId);

        assertThatThrownBy(() -> commandBus.send(MessageProvider.of(new ConfirmOrder(orderId))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("confirm was told to fail");
    }

    @Test
    void sendingACommandWithoutConsumerFails() {
        assertThatThrownBy(() -> commandBus.send(UnhandledCommand::new))
                .isInstanceOf(NoConsumerForCommandException.class)
                .hasMessageContaining(UnhandledCommand.class.getName());
    }

    @Test
    void eventReachesAllConsumers() {
        UUID orderId = UUID.randomUUID();

        eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId)));

        assertThat(consumers.emails()).containsExactly(new OrderConfirmed(orderId));
        assertThat(consumers.analytics()).containsExactly(new OrderConfirmed(orderId));
    }

    @Test
    void eventWithoutConsumersIsAllowed() {
        assertThatCode(() -> eventBus.publish(() -> new OrderCancelled(UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    void eventDispatchStopsAtTheFirstFailingConsumer() {
        UUID orderId = UUID.randomUUID();
        consumers.failEmailFor(orderId);

        assertThatThrownBy(() -> eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(consumers.invocations()).containsExactly("sendEmail");
    }

    @Test
    void eventFailurePropagatesToCaller() {
        UUID orderId = UUID.randomUUID();
        consumers.failEmailFor(orderId);

        assertThatThrownBy(() -> eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sendEmail was told to fail");
    }

    @Test
    void eventConsumersRunInAnnotatedOrder() {
        eventBus.publish(MessageProvider.of(new OrderConfirmed(UUID.randomUUID())));

        // sendEmail declares order = 1, updateAnalytics order = 2.
        assertThat(consumers.invocations()).containsExactly("sendEmail", "updateAnalytics");
    }

    private record UnhandledCommand() implements io.github.siloverse.messaging.api.Command {
    }
}
