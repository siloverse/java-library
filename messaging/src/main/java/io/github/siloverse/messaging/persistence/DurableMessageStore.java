package io.github.siloverse.messaging.persistence;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.Event;

import java.util.UUID;

/**
 * Writes messages and their deliveries durably, inside the caller's transaction.
 *
 * <p>Implementations must never open a transaction of their own: participating in the caller's
 * transaction is exactly what makes business state and messages commit together.
 */
public interface DurableMessageStore {

    /**
     * Stores a command together with the single delivery for its consumer.
     *
     * @param command the command to store
     * @return the id of the stored message
     * @throws io.github.siloverse.messaging.exception.NoConsumerForCommandException
     *         if no consumer is registered for the command type
     */
    UUID storeCommand(Command command);

    /**
     * Stores an event together with one delivery per registered consumer.
     *
     * <p>An event with no consumers is stored with zero deliveries; publishing it is not an error.
     *
     * @param event the event to store
     * @return the id of the stored message
     */
    UUID storeEvent(Event event);
}
