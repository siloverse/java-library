package io.github.siloverse.messaging.core.consumer;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.siloverse.messaging.core.api.Command;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConsumerMethodScannerTest {

    private final ConsumerMethodScanner scanner = new ConsumerMethodScanner();

    @Test
    void scansEveryConsumerDeclaredOnTheTargetClass() {
        var target = new ValidConsumers();

        var definitionsById = scanner.scan(target).stream()
                .collect(Collectors.toMap(ConsumerDefinition::id, Function.identity()));

        assertThat(definitionsById).hasSize(2);

        var eventDefinition = definitionsById.get("test.event");
        assertThat(eventDefinition.bean()).isSameAs(target);
        assertThat(eventDefinition.messageClass()).isEqualTo(TestEvent.class);
        assertThat(eventDefinition.method().getName()).isEqualTo("onEvent");
        assertThat(eventDefinition.contextParameterIndex()).isEqualTo(-1);

        var commandDefinition = definitionsById.get("test.command");
        assertThat(commandDefinition.bean()).isSameAs(target);
        assertThat(commandDefinition.messageClass()).isEqualTo(TestCommand.class);
        assertThat(commandDefinition.method().getName()).isEqualTo("onCommand");
        assertThat(commandDefinition.contextParameterIndex()).isEqualTo(-1);
    }

    @Test
    void ignoresMethodsWithoutConsumerAnnotation() {
        assertThat(scanner.scan(new NoConsumers())).isEmpty();
    }

    @Test
    void usesExplicitScanClassButKeepsProvidedInvocationTarget() {
        var target = new ValidConsumersSubclass();

        var definitions = scanner.scan(target, ValidConsumers.class);

        assertThat(definitions)
                .hasSize(2)
                .allMatch(definition -> definition.bean() == target)
                .allMatch(definition -> definition.method().getDeclaringClass() == ValidConsumers.class);
    }

    @Test
    void makesAValidPrivateConsumerInvocable()
            throws InvocationTargetException, IllegalAccessException {
        var target = new PrivateConsumerFixture();

        var definition = scanner.scan(target).getFirst();

        assertThat(definition.method().canAccess(target)).isTrue();
        definition.method().invoke(target, new TestEvent("hello"));
        assertThat(target.invocationCount()).isEqualTo(1);
    }

    @Test
    void rejectsBlankConsumerId() {
        assertThatThrownBy(() -> scanner.scan(new BlankIdConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("BlankIdConsumer#consume")
                .hasMessageContaining("blank id");
    }

    @Test
    void rejectsConsumerWithoutParameters() {
        assertThatThrownBy(() -> scanner.scan(new NoParameterConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("NoParameterConsumer#consume")
                .hasMessageContaining("takes 0");
    }

    @Test
    void rejectsConsumerWithMoreThanOneParameter() {
        assertThatThrownBy(() -> scanner.scan(new TwoParameterConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("TwoParameterConsumer#consume")
                .hasMessageContaining("takes 2");
    }

    @Test
    void rejectsParameterThatIsNotAMessage() {
        assertThatThrownBy(() -> scanner.scan(new UnsupportedMessageConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(UnsupportedMessage.class.getName())
                .hasMessageContaining("Messages must implement either Event or Command");
    }

    @Test
    void rejectsParameterWithMoreThanOneMessageKind() {
        assertThatThrownBy(() -> scanner.scan(new AmbiguousMessageConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(AmbiguousMessage.class.getName())
                .hasMessageContaining("more than one");
    }

    @Test
    void rejectsConsumerWithNonVoidReturnType() {
        assertThatThrownBy(() -> scanner.scan(new ReturningConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("ReturningConsumer#consume")
                .hasMessageContaining("return VOID");
    }

    @Test
    void rejectsConsumerDeclaredOnImmediateParentClass() {
        assertThatThrownBy(() -> scanner.scan(new ChildOfConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("ParentConsumer#consume")
                .hasMessageContaining("ChildOfConsumer")
                .hasMessageContaining("declared directly");
    }

    @Test
    void rejectsConsumerDeclaredHigherInClassHierarchy() {
        assertThatThrownBy(() -> scanner.scan(new GrandchildOfConsumer()))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("ParentConsumer#consume")
                .hasMessageContaining("GrandchildOfConsumer");
    }

    @Test
    void acceptsConsumerDeclaredOnChildOverrideWhenParentIsNotAnnotated() {
        var target = new AnnotatedChildConsumer();

        var definition = scanner.scan(target).getFirst();

        assertThat(definition.id()).isEqualTo("child.consumer");
        assertThat(definition.method().getDeclaringClass()).isEqualTo(AnnotatedChildConsumer.class);
    }

    record TestEvent(String value) implements Event {
    }

    record TestCommand(String value) implements Command {
    }

    static final class UnsupportedMessage {
    }

    static final class AmbiguousMessage implements Event, Command {
    }

    static class ValidConsumers {
        @Consumer(id = "test.event")
        public void onEvent(TestEvent event) {
        }

        @Consumer(id = "test.command")
        public void onCommand(TestCommand command) {
        }
    }

    static final class ValidConsumersSubclass extends ValidConsumers {
    }

    static final class NoConsumers {
        public void ordinaryMethod(TestEvent event) {
        }
    }

    static final class BlankIdConsumer {
        @Consumer(id = "  ")
        public void consume(TestEvent event) {
        }
    }

    static final class NoParameterConsumer {
        @Consumer(id = "no-parameter")
        public void consume() {
        }
    }

    static final class TwoParameterConsumer {
        @Consumer(id = "two-parameters")
        public void consume(TestEvent event, String context) {
        }
    }

    static final class UnsupportedMessageConsumer {
        @Consumer(id = "unsupported-message")
        public void consume(UnsupportedMessage message) {
        }
    }

    static final class AmbiguousMessageConsumer {
        @Consumer(id = "ambiguous-message")
        public void consume(AmbiguousMessage message) {
        }
    }

    static final class ReturningConsumer {
        @Consumer(id = "returning")
        public String consume(TestEvent event) {
            return event.value();
        }
    }

    static class ParentConsumer {
        @Consumer(id = "parent.consumer")
        public void consume(TestEvent event) {
        }
    }

    static final class ChildOfConsumer extends ParentConsumer {
    }

    static class MiddleConsumer extends ParentConsumer {
    }

    static final class GrandchildOfConsumer extends MiddleConsumer {
    }

    abstract static class UnannotatedParentConsumer {
        public abstract void consume(TestEvent event);
    }

    static final class AnnotatedChildConsumer extends UnannotatedParentConsumer {
        @Override
        @Consumer(id = "child.consumer")
        public void consume(TestEvent event) {
        }
    }
}

final class PrivateConsumerFixture {
    private int invocationCount;

    @Consumer(id = "private.consumer")
    private void consume(ConsumerMethodScannerTest.TestEvent event) {
        invocationCount++;
    }

    int invocationCount() {
        return invocationCount;
    }
}
