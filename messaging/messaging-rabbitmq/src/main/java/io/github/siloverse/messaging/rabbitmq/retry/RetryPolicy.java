package io.github.siloverse.messaging.rabbitmq.retry;

import java.time.Duration;
import java.util.Objects;

/**
 * How often a consumer's message is attempted before it is parked in the DLQ.
 *
 * <p>{@code maxAttempts} counts TOTAL deliveries, not retries: 3 means the original
 * delivery plus two redeliveries, then parking. 1 means no broker-level retry at all. (The listener's single immediate
 * in-process retry is separate and unaffected -- it absorbs transient blips before a delivery is ever counted as
 * failed.)
 *
 * <p>{@code delay} becomes the retry queue's {@code x-message-ttl}: the wait between
 * a failed attempt and the next delivery.
 */
public record RetryPolicy(int maxAttempts, Duration delay) {
    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "maxAttempts must be >= 1 (total deliveries including the first), was " + maxAttempts);
        }
        Objects.requireNonNull(delay, "delay must not be null");
        if (delay.isZero() || delay.isNegative()) {
            throw new IllegalArgumentException(
                    "delay must be positive, was " + delay
                            + ". Choose the wait between delivery attempts, e.g. Duration.ofSeconds(10).");
        }
    }
}
