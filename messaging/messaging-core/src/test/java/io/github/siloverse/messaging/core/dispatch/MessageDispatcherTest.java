package io.github.siloverse.messaging.core.dispatch;

import java.io.IOException;
import java.lang.reflect.Method;

import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.error.ConsumerInvocationException;
import io.github.siloverse.messaging.core.error.MessagingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MessageDispatcherTest {

    private final MessageDispatcher dispatcher = new MessageDispatcher();
    private final RecordingConsumer consumer = new RecordingConsumer();

    @Test
    void invokesConsumerWithSameMessageInstance() throws NoSuchMethodException {
        var message = new TestEvent("hello");

        dispatcher.dispatch(accessibleDefinition("consume"), message);

        assertThat(consumer.received).isSameAs(message);
    }

    @Test
    void rethrowsRuntimeExceptionWithoutWrappingIt() {
        var expected = new IllegalStateException("boom");
        consumer.failure = expected;

        assertThatThrownBy(
                () -> dispatcher.dispatch(accessibleDefinition("throwRuntime"), new TestEvent("hello")))
                .isSameAs(expected);
    }

    @Test
    void rethrowsErrorWithoutWrappingIt() {
        var expected = new AssertionError("fatal");
        consumer.failure = expected;

        assertThatThrownBy(
                () -> dispatcher.dispatch(accessibleDefinition("throwError"), new TestEvent("hello")))
                .isSameAs(expected);
    }

    @Test
    void wrapsCheckedExceptionAndPreservesCause() {
        var expected = new IOException("checked boom");
        consumer.failure = expected;

        assertThatThrownBy(
                () -> dispatcher.dispatch(accessibleDefinition("throwChecked"), new TestEvent("hello")))
                .isInstanceOf(ConsumerInvocationException.class)
                .hasMessage("Consumer threw a checked exception")
                .hasCause(expected);
    }

    @Test
    void reportsConsumerIdWhenMethodIsNotAccessible() throws NoSuchMethodException {
        var method = RecordingConsumer.class.getDeclaredMethod("privateConsume", TestEvent.class);
        var definition = definition("private-consumer", method);

        assertThatThrownBy(() -> dispatcher.dispatch(definition, new TestEvent("hello")))
                .isInstanceOf(MessagingException.class)
                .hasMessage("Cannot access consumer method private-consumer -- registration should have made it "
                        + "accessible. This is a scanner bug.")
                .hasCauseInstanceOf(IllegalAccessException.class);
    }

    @Test
    void propagatesInvalidInvocationArguments() throws NoSuchMethodException {
        var definition = accessibleDefinition("consume");

        assertThatThrownBy(() -> dispatcher.dispatch(definition, "wrong message type"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ConsumerDefinition accessibleDefinition(String methodName) throws NoSuchMethodException {
        var method = RecordingConsumer.class.getDeclaredMethod(methodName, TestEvent.class);
        method.setAccessible(true);
        return definition(methodName, method);
    }

    private ConsumerDefinition definition(String id, Method method) {
        return new ConsumerDefinition(id, TestEvent.class, consumer, method, -1);
    }

    record TestEvent(String value) implements Event {
    }

    static final class RecordingConsumer {
        private Object received;
        private Throwable failure;

        public void consume(TestEvent event) {
            received = event;
        }

        public void throwRuntime(TestEvent event) {
            throw (RuntimeException) failure;
        }

        public void throwError(TestEvent event) {
            throw (Error) failure;
        }

        public void throwChecked(TestEvent event) throws IOException {
            throw (IOException) failure;
        }

        private void privateConsume(TestEvent event) {
            received = event;
        }
    }
}
