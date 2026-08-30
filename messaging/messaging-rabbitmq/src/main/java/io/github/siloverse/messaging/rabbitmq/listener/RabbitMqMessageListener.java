package io.github.siloverse.messaging.rabbitmq.listener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.consumer.Inbox;
import io.github.siloverse.messaging.core.dispatch.MessageDispatcher;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;
import io.github.siloverse.messaging.core.transport.PayloadDeserializer;
import io.github.siloverse.messaging.rabbitmq.retry.FailedDeliveryHandler;
import io.github.siloverse.messaging.rabbitmq.topology.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes each registered consumer's queue and dispatches typed messages to its {@code @Consumer} method.
 *
 * <p>One channel per consumer, prefetch 1, manual acks. Success acks. Failure escalates in
 * tiers: a first failure is nacked WITH requeue -- one immediate retry, invisible to x-death, absorbs transients; a
 * redelivered failure is handed to the {@link FailedDeliveryHandler}, which either nacks it into the consumer's
 * {@code .retry} queue (whose TTL returns it after the policy delay) or, when attempts are exhausted, republishes it to
 * the {@code .dlq} and acks the original -- bounded attempts, never a loop, never a loss. Nothing ever propagates into
 * the client's consumer thread: an escaped exception there kills the consumer silently.
 *
 * <p>The connection is borrowed (the consuming side's own connection, separate from the
 * publishing one -- broker flow control throttles publishers and must not starve consumers); the channels are owned:
 * opened on {@code start()}, closed on {@code stop()}. Unacked in-flight deliveries at stop are requeued by the broker
 * as redelivered.
 */
public class RabbitMqMessageListener {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqMessageListener.class);

    private static final int PREFETCH = 1;

    private final Connection connection;
    private final String serviceName;
    private final ConsumerRegistry consumerRegistry;
    private final MessageNameRegistry messageNameRegistry;
    private final PayloadDeserializer payloadDeserializer;
    private final MessageDispatcher dispatcher;
    private final FailedDeliveryHandler failedDeliveryHandler;
    private final Inbox inbox; // null = no dedup consumers allowed

    private final List<Channel> channels = new ArrayList<>();
    private boolean started;

    /** Without an {@link Inbox}: every consumer must be idempotent; {@code dedup = true} is rejected at start. */
    public RabbitMqMessageListener(Connection connection, String serviceName, ConsumerRegistry consumerRegistry,
            MessageNameRegistry messageNameRegistry, PayloadDeserializer payloadDeserializer,
            MessageDispatcher dispatcher, FailedDeliveryHandler failedDeliveryHandler) {
        this(connection, serviceName, consumerRegistry, messageNameRegistry, payloadDeserializer, dispatcher, null,
                failedDeliveryHandler);
    }

    public RabbitMqMessageListener(Connection connection, String serviceName, ConsumerRegistry consumerRegistry,
            MessageNameRegistry messageNameRegistry, PayloadDeserializer payloadDeserializer,
            MessageDispatcher dispatcher, Inbox inbox, FailedDeliveryHandler failedDeliveryHandler) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(consumerRegistry, "consumerRegistry must not be null");
        Objects.requireNonNull(messageNameRegistry, "messageNameRegistry must not be null");
        Objects.requireNonNull(payloadDeserializer, "payloadDeserializer must not be null");
        Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(failedDeliveryHandler, "failedDeliveryHandler must not be null");

        if (serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank");
        }
        this.connection = connection;
        this.consumerRegistry = consumerRegistry;
        this.messageNameRegistry = messageNameRegistry;
        this.payloadDeserializer = payloadDeserializer;
        this.dispatcher = dispatcher;
        this.inbox = inbox;
        this.serviceName = serviceName;
        this.failedDeliveryHandler = failedDeliveryHandler;
    }

    public synchronized void start() {
        if (started) {
            return;
        }
        List<Channel> openedChannels = new ArrayList<>();
        for (ConsumerDefinition definition : consumerRegistry.allConsumers()) {
            if (definition.dedup() && inbox == null) {
                throw new MessagingConfigurationException(
                        "Consumer '" + definition.id() + "' declares dedup = true but no Inbox was" +
                                " provided to the listener. Deduplication is a promise the listener" +
                                " cannot keep without one -- construct the listener with an Inbox" +
                                " (e.g. JdbcInbox over the consumer's database) or remove the flag.");
            }
        }
        for (ConsumerDefinition definition : consumerRegistry.allConsumers()) {
            String queueName = QueueNames.mainQueueName(serviceName, definition);
            try {
                Channel channel = connection.createChannel();
                openedChannels.add(channel);

                channel.basicQos(PREFETCH);
                channel.basicConsume(queueName, false,
                        (consumerTag, delivery) -> handleDelivery(channel, definition, delivery),
                        consumerTag -> log.warn("Broker cancelled consumer '{}' on queue '{}' (queue deleted?);" +
                                " this consumer receives nothing until restart", definition.id(), queueName));
            } catch (IOException | ShutdownSignalException e) {
                // no partial listeners: what was opened before the failure must not keep consuming
                closeQuietly(openedChannels);
                throw new MessagingException(
                        "Could not start consumer '" + definition.id() + "' on queue '" + queueName +
                                "'. Verify topology was declared before listeners start (the queue" +
                                " must exist) and the connection is open.", e);
            }
        }

        channels.addAll(openedChannels);
        started = true;
        log.info("Listening on {} consumer queue(s) for service '{}'", channels.size(), serviceName);
    }

    public synchronized void stop() {
        if (!started) {
            return;
        }

        closeQuietly(channels);
        channels.clear();

        started = false;
        log.info("Stopped consuming for service '{}'", serviceName);
    }

    private void handleDelivery(Channel channel, ConsumerDefinition definition, Delivery delivery) throws IOException {
        try {
            verifyMessageType(definition, delivery);

            Object message = payloadDeserializer.deserialize(delivery.getBody(), definition.messageClass());
            if (definition.dedup()) {
                var messageId = UUID.fromString(delivery.getProperties().getMessageId());
                boolean processed = inbox.processOnce(definition.id(), messageId,
                        () -> dispatcher.dispatch(definition, stringHeaders(delivery.getProperties().getHeaders()),
                                message));
                if (!processed) {
                    log.debug("Duplicate message {} for consumer '{}' skipped by the inbox", messageId,
                            definition.id());
                }
            } else {
                dispatcher.dispatch(definition, stringHeaders(delivery.getProperties().getHeaders()), message);
            }
            // the duplicate is acked exactly like a processed message: consumed either way
            channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        } catch (Exception processingFailure) {
            if (!delivery.getEnvelope().isRedeliver()) {
                log.warn("Consumer '{}' failed on message {} -- retrying once immediately", definition.id(),
                        delivery.getProperties().getMessageId(), processingFailure);
                try {
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                } catch (IOException | RuntimeException nackFailure) {
                    log.warn(
                            "Could not nack message {} for immediate retry (channel closing?); the broker will redeliver",
                            delivery.getProperties().getMessageId(),
                            nackFailure);
                }
            } else {
                try {
                    failedDeliveryHandler.handle(channel, delivery, definition, processingFailure);
                } catch (IOException | RuntimeException handlerFailure) {
                    log.warn("Failure handling for message {} on consumer '{}' itself failed (channel closing?);" +
                                    " the broker will redeliver", delivery.getProperties().getMessageId(), definition.id(),
                            handlerFailure);
                }
            }
        }
    }

    private void verifyMessageType(ConsumerDefinition definition, Delivery delivery) {
        String messageName = delivery.getEnvelope().getRoutingKey();

        // dead-lettering rewrites the routing key: a delivery re-entering from the retry queue (or
        // one a human replays from the DLQ) arrives via the default exchange carrying the queue's own name. The queue
        // is bound to exactly one message type's exchange, so the queue itself vouches for the type.
        if (messageName.equals(QueueNames.mainQueueName(serviceName, definition))) {
            return;
        }

        Class<?> deliveredType = messageNameRegistry.classOf(messageName);

        if (!definition.messageClass().equals(deliveredType)) {
            throw new IllegalStateException("Queue topology mismatch. Consumer expects %s but received %s".formatted(
                    definition.messageClass().getName(), deliveredType.getName()));
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

    private static Map<String, String> stringHeaders(Map<String, Object> amqpHeaders) {
        if (amqpHeaders == null) {                 // ← real case: a message with NO headers
            return Map.of();
        }
        var headers = new HashMap<String, String>();
        amqpHeaders.forEach((key, value) -> headers.put(key, String.valueOf(value)));
        return headers;
    }
}
