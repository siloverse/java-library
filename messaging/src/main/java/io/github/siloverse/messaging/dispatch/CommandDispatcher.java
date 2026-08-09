package io.github.siloverse.messaging.dispatch;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.consumer.ConsumerDescriptor;
import io.github.siloverse.messaging.consumer.ConsumerInvoker;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.exception.NoConsumerForCommandException;
import org.springframework.util.Assert;

/**
 * Delivers a command to its single consumer, failing when there is none.
 */
public class CommandDispatcher implements MessageDispatcher {

    private final ConsumerRegistry registry;
    private final ConsumerInvoker invoker;

    public CommandDispatcher(ConsumerRegistry registry, ConsumerInvoker invoker) {
        this.registry = registry;
        this.invoker = invoker;
    }

    @Override
    public void dispatch(Message message) {
        Assert.isInstanceOf(Command.class, message, "CommandDispatcher only dispatches commands: ");
        dispatch((Command) message);
    }

    /**
     * @param command the command to deliver
     * @throws NoConsumerForCommandException if the command type has no consumer
     */
    public void dispatch(Command command) {
        ConsumerDescriptor consumer = registry.requireCommandConsumer(command.getClass());
        invoker.invoke(consumer, command);
    }
}
