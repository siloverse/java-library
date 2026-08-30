package io.github.siloverse.messaging.rabbitmq.retry;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Delivery;
import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.rabbitmq.topology.QueueNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounds the retry topology: counts attempts from the broker's {@code x-death} audit trail
 * (grouped by queue and reason -- only rejections from the main queue count) and either nacks the delivery into the
 * {@code .retry} queue for another delayed round, or, when the policy's attempts are exhausted, parks it terminally.
 *
 * <p>Parking must be an active republish: the main queue's dead-letter routing is fixed at
 * declaration time (it points at {@code .retry}), so a final nack cannot choose the {@code .dlq}. The republish is
 * confirmed before the original is acked -- a crash between the two re-delivers the original, giving at-least-once
 * parking; the rare duplicate is absorbed downstream (dedup inbox) or tolerated in a queue read by humans. The parked
 * copy carries {@code x-parking-cause} (the abbreviated failure) alongside the broker's own {@code x-death} history.
 */
public class RetryingFailedDeliveryHandler implements FailedDeliveryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryingFailedDeliveryHandler.class);

    private final RetrySettings retrySettings;
    private final String serviceName;

    public RetryingFailedDeliveryHandler(RetrySettings retrySettings, String serviceName) {
        this.retrySettings = Objects.requireNonNull(retrySettings, "retrySettings must not be null");
        this.serviceName = Objects.requireNonNull(serviceName, "serviceName must not be null");
    }

    @Override
    public void handle(Channel channel, Delivery delivery, ConsumerDefinition consumerDefinition, Throwable cause)
            throws IOException {
        var retryPolicy = retrySettings.policyFor(consumerDefinition);
        var mainQueue = QueueNames.mainQueueName(serviceName, consumerDefinition);
        var attemptsSoFar = priorRejections(delivery, mainQueue) + 1;

        if (attemptsSoFar >= retryPolicy.maxAttempts()) {
            parkInDlq(channel, delivery, consumerDefinition, cause);
            log.error("Consumer '{}' exhausted {} attempts for message {} -- parked in DLQ",
                    consumerDefinition.id(), retryPolicy.maxAttempts(), delivery.getProperties().getMessageId(), cause);
            return;
        }

        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
        log.warn("Consumer '{}' failed attempt {}/{} for message {} -- retrying in {}",
                consumerDefinition.id(), attemptsSoFar, retryPolicy.maxAttempts(),
                delivery.getProperties().getMessageId(), retryPolicy.delay());
    }

    private int priorRejections(Delivery delivery, String mainQueue) {
        var headers = delivery.getProperties().getHeaders();
        if (headers == null || !(headers.get("x-death") instanceof List<?> deaths)) {
            return 0;
        }
        for (Object entry : deaths) {
            if (entry instanceof Map<?, ?> death
                    && mainQueue.equals(String.valueOf(death.get("queue")))
                    && "rejected".equals(String.valueOf(death.get("reason")))) {
                return ((Number) death.get("count")).intValue();
            }
        }
        return 0;
    }

    private void parkInDlq(Channel channel, Delivery delivery, ConsumerDefinition consumerDefinition, Throwable cause)
            throws IOException {
        var headers = copyHeaders(delivery);
        headers.put("x-parking-cause", abbreviate(cause));
        AMQP.BasicProperties parked = delivery
                .getProperties()
                .builder()
                .headers(headers)
                .build();
        channel.confirmSelect();
        channel.basicPublish("", QueueNames.dlqName(serviceName, consumerDefinition), parked, delivery.getBody());
        try {
            channel.waitForConfirmsOrDie(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException("Interrupted awaiting DLQ publish confirm", e);
        } catch (TimeoutException e) {
            throw new MessagingException(
                    "Broker did not confirm DLQ publish in time -- original left unacked for redelivery", e);
        }
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    }

    private static String abbreviate(Throwable cause) {
        String text = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return text.length() <= 300 ? text : text.substring(0, 300);
    }

    private Map<String, Object> copyHeaders(Delivery delivery) {
        var headers = delivery.getProperties().getHeaders();

        return headers == null
                ? new HashMap<>()
                : new HashMap<>(headers);
    }
}
