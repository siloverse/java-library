package io.github.siloverse.messaging.persistence;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.api.MessageKind;
import io.github.siloverse.messaging.consumer.ConsumerDescriptor;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.exception.TransactionRequiredException;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import io.github.siloverse.messaging.persistence.entity.StoredMessage;
import io.github.siloverse.messaging.persistence.repository.MessageDeliveryRepository;
import io.github.siloverse.messaging.persistence.repository.MessageRepository;
import io.github.siloverse.messaging.serialization.MessageSerializer;
import io.github.siloverse.messaging.serialization.SerializedMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stores messages through Spring Data JPA, so the writes join whatever transaction the caller
 * already has open.
 */
public class JpaDurableMessageStore implements DurableMessageStore {

    private static final Log logger = LogFactory.getLog(JpaDurableMessageStore.class);

    private final MessageRepository messages;
    private final MessageDeliveryRepository deliveries;
    private final ConsumerRegistry registry;
    private final MessageSerializer serializer;
    private final Clock clock;

    public JpaDurableMessageStore(MessageRepository messages,
                                  MessageDeliveryRepository deliveries,
                                  ConsumerRegistry registry,
                                  MessageSerializer serializer,
                                  Clock clock) {
        this.messages = messages;
        this.deliveries = deliveries;
        this.registry = registry;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    public UUID storeCommand(Command command) {
        ConsumerDescriptor consumer = registry.requireCommandConsumer(command.getClass());
        return store(command, MessageKind.COMMAND, List.of(consumer));
    }

    @Override
    public UUID storeEvent(Event event) {
        return store(event, MessageKind.EVENT, registry.findEventConsumers(event.getClass()));
    }

    private UUID store(Message message, MessageKind kind, List<ConsumerDescriptor> targets) {
        requireTransaction(message);

        Instant now = clock.instant();
        SerializedMessage serialized = serializer.serialize(message);
        StoredMessage stored = StoredMessage.create(
                UUID.randomUUID(), serialized.type(), kind, serialized.payload(), now);
        messages.save(stored);

        for (ConsumerDescriptor target : targets) {
            deliveries.save(MessageDelivery.pending(
                    UUID.randomUUID(), stored.getId(), target.consumerId(), now));
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Stored " + kind + " " + serialized.type() + " as " + stored.getId()
                    + " with " + targets.size() + " delivery(ies)");
        }
        return stored.getId();
    }

    private void requireTransaction(Message message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new TransactionRequiredException(
                    "Storing " + message.getClass().getName() + " requires an active transaction so "
                            + "that the message commits together with the business changes that "
                            + "caused it.");
        }
    }
}
