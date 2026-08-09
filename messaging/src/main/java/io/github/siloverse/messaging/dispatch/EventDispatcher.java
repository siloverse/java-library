package io.github.siloverse.messaging.dispatch;

import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.consumer.ConsumerDescriptor;
import io.github.siloverse.messaging.consumer.ConsumerInvoker;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Delivers an event to every registered consumer, in {@code order()} sequence.
 *
 * <p>Dispatch is fail fast: the first consumer that throws aborts the remaining ones and the
 * exception reaches the caller. Synchronous dispatch runs inside the caller's transaction, so
 * swallowing the failure and continuing would be worse than stopping.
 */
public class EventDispatcher implements MessageDispatcher {

    private final ConsumerRegistry registry;
    private final ConsumerInvoker invoker;

    public EventDispatcher(ConsumerRegistry registry, ConsumerInvoker invoker) {
        this.registry = registry;
        this.invoker = invoker;
    }

    @Override
    public void dispatch(Message message) {
        Assert.isInstanceOf(Event.class, message, "EventDispatcher only dispatches events: ");
        dispatch((Event) message);
    }

    /**
     * @param event the event to deliver; having no consumers is not an error
     */
    public void dispatch(Event event) {
        List<ConsumerDescriptor> consumers = registry.findEventConsumers(event.getClass());
        for (ConsumerDescriptor consumer : consumers) {
            invoker.invoke(consumer, event);
        }
    }
}
