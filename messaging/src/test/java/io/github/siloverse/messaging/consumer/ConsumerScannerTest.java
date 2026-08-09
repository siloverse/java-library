package io.github.siloverse.messaging.consumer;

import io.github.siloverse.messaging.annotation.Consumer;
import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.MessageKind;
import io.github.siloverse.messaging.exception.ConsumerDefinitionException;
import io.github.siloverse.messaging.fixture.ConfirmOrder;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.fixture.RecordingConsumers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsumerScannerTest {

    private final ConsumerScanner scanner = new ConsumerScanner(new DefaultConsumerIdStrategy());

    @Test
    void findsValidConsumerMethods() {
        List<ConsumerDescriptor> descriptors = scanner.scanBean("recordingConsumers", RecordingConsumers.class);

        assertThat(descriptors)
                .extracting(descriptor -> descriptor.method().getName())
                .containsExactlyInAnyOrder("confirm", "sendEmail", "updateAnalytics");
    }

    @Test
    void classifiesCommandsAndEvents() {
        List<ConsumerDescriptor> descriptors = scanner.scanBean("recordingConsumers", RecordingConsumers.class);

        assertThat(descriptors)
                .filteredOn(descriptor -> descriptor.messageType().equals(ConfirmOrder.class))
                .singleElement()
                .satisfies(descriptor -> assertThat(descriptor.kind()).isEqualTo(MessageKind.COMMAND));
        assertThat(descriptors)
                .filteredOn(descriptor -> descriptor.messageType().equals(OrderConfirmed.class))
                .hasSize(2)
                .allSatisfy(descriptor -> assertThat(descriptor.kind()).isEqualTo(MessageKind.EVENT));
    }

    @Test
    void discoversMultipleConsumersForOneEvent() {
        List<ConsumerDescriptor> descriptors = scanner.scanBean("recordingConsumers", RecordingConsumers.class);

        assertThat(descriptors)
                .filteredOn(descriptor -> descriptor.messageType().equals(OrderConfirmed.class))
                .extracting(descriptor -> descriptor.method().getName())
                .containsExactlyInAnyOrder("sendEmail", "updateAnalytics");
    }

    @Test
    void consumerIdsAreStableAndDistinct() {
        List<ConsumerDescriptor> first = scanner.scanBean("recordingConsumers", RecordingConsumers.class);
        List<ConsumerDescriptor> second = scanner.scanBean("recordingConsumers", RecordingConsumers.class);

        assertThat(first).extracting(ConsumerDescriptor::consumerId)
                .containsExactlyInAnyOrderElementsOf(second.stream().map(ConsumerDescriptor::consumerId).toList());
        assertThat(first).extracting(ConsumerDescriptor::consumerId).doesNotHaveDuplicates();
        assertThat(first).allSatisfy(descriptor ->
                assertThat(descriptor.consumerId()).contains("recordingConsumers", RecordingConsumers.class.getName()));
    }

    @Test
    void rejectsMultipleArguments() {
        assertThatThrownBy(() -> scanner.scanBean("bean", TooManyArguments.class))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("exactly one argument");
    }

    @Test
    void rejectsZeroArguments() {
        assertThatThrownBy(() -> scanner.scanBean("bean", NoArguments.class))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("exactly one argument");
    }

    @Test
    void rejectsNonMessageArgument() {
        assertThatThrownBy(() -> scanner.scanBean("bean", NotAMessage.class))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("must accept a Command or an Event");
    }

    @Test
    void rejectsNonVoidReturnType() {
        assertThatThrownBy(() -> scanner.scanBean("bean", NotVoid.class))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("must return void");
    }

    @Test
    void rejectsStaticMethods() {
        assertThatThrownBy(() -> scanner.scanBean("bean", StaticConsumer.class))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("must not be static");
    }

    @Test
    void rejectsAbstractMessageTypesThatCouldNeverMatch() {
        assertThatThrownBy(() -> scanner.scanBean("bean", AbstractMessageArgument.class))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("could never be invoked");
    }

    @Test
    void rejectsMessagesThatAreBothCommandAndEvent() {
        assertThatThrownBy(() -> scanner.scanBean("bean", AmbiguousMessageArgument.class))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("exactly one of Command or Event");
    }

    @Test
    void detectsMultipleConsumersForOneCommand() {
        ConsumerRegistry registry = new ConsumerRegistry(scanner, new DefaultListableBeanFactory());

        assertThatThrownBy(() -> registry.register(scanner.scanBean("bean", TwoCommandConsumers.class)))
                .isInstanceOf(ConsumerDefinitionException.class)
                .hasMessageContaining("must have exactly one @Consumer method");
    }

    @Test
    void allowsOneCommandConsumerPerCommandType() {
        ConsumerRegistry registry = new ConsumerRegistry(scanner, new DefaultListableBeanFactory());

        assertThatCode(() -> registry.register(scanner.scanBean("recordingConsumers", RecordingConsumers.class)))
                .doesNotThrowAnyException();
        assertThat(registry.findCommandConsumer(ConfirmOrder.class)).isPresent();
        assertThat(registry.findEventConsumers(OrderConfirmed.class)).hasSize(2);
    }

    @Test
    void scansBeansOfAnApplicationContext() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(RecordingConsumers.class)) {
            List<ConsumerDescriptor> descriptors = scanner.scan(context.getBeanFactory());

            assertThat(descriptors).hasSize(3);
        }
    }

    @SuppressWarnings("unused")
    static class TooManyArguments {
        @Consumer
        void handle(ConfirmOrder command, String extra) {
        }
    }

    @SuppressWarnings("unused")
    static class NoArguments {
        @Consumer
        void handle() {
        }
    }

    @SuppressWarnings("unused")
    static class NotAMessage {
        @Consumer
        void handle(String notAMessage) {
        }
    }

    @SuppressWarnings("unused")
    static class NotVoid {
        @Consumer
        boolean handle(ConfirmOrder command) {
            return true;
        }
    }

    @SuppressWarnings("unused")
    static class StaticConsumer {
        @Consumer
        static void handle(ConfirmOrder command) {
        }
    }

    @SuppressWarnings("unused")
    static class AbstractMessageArgument {
        @Consumer
        void handle(Event event) {
        }
    }

    @SuppressWarnings("unused")
    static class TwoCommandConsumers {
        @Consumer
        void first(ConfirmOrder command) {
        }

        @Consumer
        void second(ConfirmOrder command) {
        }
    }

    @SuppressWarnings("unused")
    static class BothCommandAndEvent implements Command, Event {
    }

    @SuppressWarnings("unused")
    static class AmbiguousMessageArgument {
        @Consumer
        void handle(BothCommandAndEvent ambiguous) {
        }
    }
}
