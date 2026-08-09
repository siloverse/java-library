package io.github.siloverse.messaging.async;

import io.github.siloverse.messaging.api.Message;
import io.github.siloverse.messaging.consumer.ConsumerDescriptor;
import io.github.siloverse.messaging.consumer.ConsumerInvoker;
import io.github.siloverse.messaging.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.exception.NonRetryableMessagingException;
import io.github.siloverse.messaging.exception.UnknownConsumerException;
import io.github.siloverse.messaging.persistence.entity.DeliveryStatus;
import io.github.siloverse.messaging.persistence.entity.MessageDelivery;
import io.github.siloverse.messaging.persistence.entity.StoredMessage;
import io.github.siloverse.messaging.persistence.repository.MessageDeliveryRepository;
import io.github.siloverse.messaging.persistence.repository.MessageRepository;
import io.github.siloverse.messaging.serialization.MessageSerializer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Runs one claimed delivery: load, deserialize, invoke the consumer, record the outcome.
 *
 * <p>The happy path runs in a single transaction, so whatever the consumer writes to the database
 * commits together with the delivery being marked {@code PROCESSED}. When the consumer throws, that
 * transaction is rolled back, undoing the consumer's partial writes, and the failure is recorded in
 * a second, independent transaction so the attempt count and error survive.
 */
public class MessageProcessor {

    private static final Log logger = LogFactory.getLog(MessageProcessor.class);

    private static final int MAX_ERROR_LENGTH = 4000;

    private final TransactionTemplate transactionTemplate;
    private final MessageDeliveryRepository deliveries;
    private final MessageRepository messages;
    private final MessageSerializer serializer;
    private final ConsumerRegistry registry;
    private final ConsumerInvoker invoker;
    private final Clock clock;
    private final int maxAttempts;
    private final Duration retryDelay;

    public MessageProcessor(TransactionTemplate transactionTemplate,
                            MessageDeliveryRepository deliveries,
                            MessageRepository messages,
                            MessageSerializer serializer,
                            ConsumerRegistry registry,
                            ConsumerInvoker invoker,
                            Clock clock,
                            int maxAttempts,
                            Duration retryDelay) {
        this.transactionTemplate = transactionTemplate;
        this.deliveries = deliveries;
        this.messages = messages;
        this.serializer = serializer;
        this.registry = registry;
        this.invoker = invoker;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
    }

    /**
     * Processes a delivery that the poller has already claimed.
     *
     * @param deliveryId the claimed delivery
     */
    public void process(UUID deliveryId) {
        try {
            transactionTemplate.executeWithoutResult(status -> deliver(deliveryId));
        }
        catch (Exception ex) {
            recordFailure(deliveryId, ex);
        }
        catch (Error error) {
            recordFailure(deliveryId, error);
            throw error;
        }
    }

    private void deliver(UUID deliveryId) {
        MessageDelivery delivery = deliveries.findById(deliveryId).orElse(null);
        if (delivery == null) {
            logger.warn("Claimed delivery " + deliveryId + " no longer exists, skipping");
            return;
        }
        if (delivery.getStatus() != DeliveryStatus.PROCESSING) {
            // Another worker already finished it, or a stale lock recovery released it.
            logger.debug("Delivery " + deliveryId + " is " + delivery.getStatus() + ", skipping");
            return;
        }

        StoredMessage stored = messages.findById(delivery.getMessageId())
                .orElseThrow(() -> new NonRetryableMessagingException(
                        "Delivery " + deliveryId + " references unknown message " + delivery.getMessageId()));

        Message message = serializer.deserialize(stored.getMessageType(), stored.getPayload());
        ConsumerDescriptor consumer = registry.findById(delivery.getConsumerId())
                .orElseThrow(() -> new UnknownConsumerException(delivery.getConsumerId()));

        invoker.invoke(consumer, message);

        delivery.markProcessed(clock.instant());
        deliveries.save(delivery);
    }

    private void recordFailure(UUID deliveryId, Throwable failure) {
        boolean retryable = !(failure instanceof NonRetryableMessagingException);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                MessageDelivery delivery = deliveries.findById(deliveryId).orElse(null);
                if (delivery == null) {
                    return;
                }
                Instant now = clock.instant();
                String error = describe(failure);
                if (!retryable || delivery.getAttempts() >= maxAttempts) {
                    delivery.markFailed(now, error);
                    logger.error("Delivery " + deliveryId + " failed permanently after "
                            + delivery.getAttempts() + " attempt(s)", failure);
                }
                else {
                    delivery.markForRetry(now.plus(retryDelay), error);
                    logger.warn("Delivery " + deliveryId + " failed on attempt "
                            + delivery.getAttempts() + " of " + maxAttempts + ", retrying after "
                            + retryDelay + ": " + failure);
                }
                deliveries.save(delivery);
            });
        }
        catch (RuntimeException ex) {
            // The delivery stays PROCESSING and its lock will expire, so it is not lost.
            logger.error("Could not record the failure of delivery " + deliveryId
                    + ", it will be reclaimed once its lock expires", ex);
        }
    }

    private String describe(Throwable failure) {
        StringWriter writer = new StringWriter();
        failure.printStackTrace(new PrintWriter(writer));
        String text = writer.toString();
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
