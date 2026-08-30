package io.github.siloverse.messaging.rabbitmq.retry;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;

/**
 * Decides the fate of a delivery whose consumer threw: another attempt, or terminal parking. Invoked by the listener
 * AFTER its single immediate in-process retry has also failed -- by the time this strategy runs, the delivery has
 * genuinely failed.
 *
 * <p>Implementations own the channel interaction for the failure path (nack,
 * republish, ack) -- the listener hands over the decision entirely. Whatever the outcome, the original delivery must
 * end up acknowledged or dead-lettered exactly once from the broker's point of view; crash windows inside an
 * implementation may surface as redeliveries, which consumers absorb (dedup inbox) by contract.
 */
public interface FailedDeliveryHandler {

    void handle(Channel channel, Delivery delivery, ConsumerDefinition definition, Throwable cause)
            throws IOException;
}
