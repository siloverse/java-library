package io.github.siloverse.messaging.rabbitmq.listener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.dispatch.MessageDispatcher;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.PayloadDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes each registered consumer's queue and dispatches typed messages to its {@code @Consumer} method.
 *
 * <p>One channel per consumer, prefetch 1, manual acks. Success acks. Failure follows the
 * two-attempt policy: a first failure is nacked WITH requeue (one immediate retry covers transients); a redelivered
 * failure is nacked WITHOUT requeue and parks in the queue's {@code .dlq} -- bounded attempts, never a loop, never a
 * loss. Nothing ever propagates into the client's consumer thread: an escaped exception there kills the consumer
 * silently.
 *
 * <p>The connection is borrowed (the consuming side's own connection, separate from the
 * publishing one -- broker flow control throttles publishers and must not starve consumers); the channels are owned:
 * opened on {@code start()}, closed on {@code stop()}. Unacked in-flight deliveries at stop are requeued by the broker
 * as redelivered.
 */
public class RabbitMqMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMqMessageListener.class);

    private static final int PREFETCH = 1;

    private final Connection connection;
    private final String serviceName;
    private final ConsumerRegistry consumerRegistry;
    private final MessageNameRegistry messageNameRegistry;
    private final PayloadDeserializer payloadDeserializer;
    private final MessageDispatcher dispatcher;

    private final List<Channel> channels = new ArrayList<>();
    private boolean started;

    public RabbitMqMessageListener(Connection connection,
            String serviceName,
            ConsumerRegistry consumerRegistry,
            MessageNameRegistry messageNameRegistry,
            PayloadDeserializer payloadDeserializer,
            MessageDispatcher dispatcher) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(consumerRegistry, "consumerRegistry must not be null");
        Objects.requireNonNull(messageNameRegistry, "messageNameRegistry must not be null");
        Objects.requireNonNull(payloadDeserializer, "payloadDeserializer must not be null");
        Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        Objects.requireNonNull(serviceName, "serviceName must not be null");

        if (serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        this.connection = connection;
        this.consumerRegistry = consumerRegistry;
        this.messageNameRegistry = messageNameRegistry;
        this.payloadDeserializer = payloadDeserializer;
        this.dispatcher = dispatcher;
        this.serviceName = serviceName;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        List<Channel> openedChannels = new ArrayList<>();
        for (ConsumerDefinition definition : consumerRegistry.allConsumers()) {
            String queueName = queueName(definition);
            try {
                Channel channel = connection.createChannel();
                openedChannels.add(channel);

                channel.basicQos(PREFETCH);
                channel.basicConsume(
                        queueName,
                        false,
                        (consumerTag, delivery) ->
                                handleDelivery(channel, definition, delivery),
                        consumerTag -> logger.warn(
                                "Broker cancelled consumer '{}' on queue '{}' (queue deleted?);"
                                        + " this consumer receives nothing until restart",
                                definition.id(), queueName)
                );
            } catch (IOException | ShutdownSignalException e) {
                // no partial listeners: what was opened before the failure must not keep consuming
                closeQuietly(openedChannels);
                throw new MessagingException(
                        "Could not start consumer '" + definition.id() + "' on queue '" + queueName
                                + "'. Verify topology was declared before listeners start (the queue"
                                + " must exist) and the connection is open.", e);
            }
        }

        channels.addAll(openedChannels);
        started = true;
        logger.info("Listening on {} consumer queue(s) for service '{}'", channels.size(), serviceName);
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }

        closeQuietly(channels);
        channels.clear();

        started = false;
        logger.info("Stopped consuming for service '{}'", serviceName);
    }

    private void handleDelivery(
            Channel channel,
            ConsumerDefinition definition,
            Delivery delivery
    ) {
        try {
            verifyMessageType(definition, delivery);

            Object message = payloadDeserializer.deserialize(
                    delivery.getBody(),
                    definition.messageClass()
            );
            dispatcher.dispatch(definition, message);
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        } catch (Exception processingFailure) {
            reject(channel, definition, delivery, processingFailure);
        }
    }

    private void reject(
            Channel channel,
            ConsumerDefinition definition,
            Delivery delivery,
            Exception cause
    ) {
        long deliveryTag = delivery.getEnvelope().getDeliveryTag();
        boolean redelivered = delivery.getEnvelope().isRedeliver();
        String messageId = delivery.getProperties().getMessageId();

        try {
            if (!redelivered) {
                logger.warn("Consumer '{}' failed on message {} -- retrying once immediately",
                        definition.id(), messageId, cause);
                channel.basicNack(deliveryTag, false, true);
            } else {
                logger.error("Consumer '{}' failed on redelivered message {} -- PARKING it in '{}.dlq'."
                                + " It waits there for a human: fix the cause, then replay it.",
                        definition.id(), messageId, queueName(definition), cause);
                channel.basicNack(deliveryTag, false, false);
            }
        } catch (IOException | ShutdownSignalException nackFailure) {
            // channel is dying; the unacked delivery will be requeued by the broker anyway --
            // log instead of letting anything escape into the client's consumer thread
            logger.warn("Could not nack message {} for consumer '{}' (channel closing?);"
                    + " the broker will requeue it", messageId, definition.id(), nackFailure);
        }
    }

    private String queueName(ConsumerDefinition definition) {
        return "%s-%s".formatted(
                serviceName,
                definition.id()
        );
    }

    private void verifyMessageType(
            ConsumerDefinition definition,
            Delivery delivery
    ) {
        String messageName = delivery.getEnvelope().getRoutingKey();

        Class<?> deliveredType = messageNameRegistry.classOf(messageName);

        if (!definition.messageClass().equals(deliveredType)) {
            throw new IllegalStateException(
                    "Queue topology mismatch. Consumer expects %s but received %s"
                            .formatted(
                                    definition.messageClass().getName(),
                                    deliveredType.getName()
                            )
            );
        }
    }

    private void closeQuietly(List<Channel> channels) {
        for (Channel channel : channels) {
            try {
                if (channel.isOpen()) {
                    channel.close();
                }
            } catch (Exception ignored) {
            }
        }
    }
}
