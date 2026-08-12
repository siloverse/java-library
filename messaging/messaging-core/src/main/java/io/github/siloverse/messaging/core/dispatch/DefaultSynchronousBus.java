package io.github.siloverse.messaging.core.dispatch;

import io.github.siloverse.messaging.core.api.Command;
import io.github.siloverse.messaging.core.api.Event;
import io.github.siloverse.messaging.core.api.SynchronousBus;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;

public class DefaultSynchronousBus implements SynchronousBus {

    private final ConsumerRegistry registry;
    private final MessageDispatcher messageDispatcher;

    public DefaultSynchronousBus(
            ConsumerRegistry consumerRegistry,
            MessageDispatcher messageDispatcher
    ) {
        this.registry = consumerRegistry;
        this.messageDispatcher = messageDispatcher;
    }

    @Override
    public void publish(Event event) {
        for (ConsumerDefinition def : registry.eventConsumersFor(event.getClass())) {
            messageDispatcher.dispatch(def, event);
        }
    }

    @Override
    public void send(Command command) {
        var def = registry.commandConsumerFor(command.getClass());
        messageDispatcher.dispatch(def, command);
    }
}
