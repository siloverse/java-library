package io.github.siloverse.messaging.rabbitmq.consumer;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-consumer RabbitMQ policy, declared on a {@code @Consumer} method. On any other method it is
 * silently inert -- nothing scans for it there.
 *
 * <p>Every unset element inherits the effective system default. Precedence, highest first:
 * this annotation, then the {@code messaging.retry.default-policy} configuration, then the
 * built-in {@link io.github.siloverse.messaging.rabbitmq.retry.RetrySettings#DEFAULT}. Inheritance
 * is per element: {@code @RabbitPolicy(maxAttempts = 5)} raises the attempts and keeps the default
 * delay -- never restate a default just to change its neighbour.
 *
 * <p><b>Changing {@code delayMillis} is a broker migration, not a casual edit.</b> The delay is
 * declared as the retry queue's {@code x-message-ttl}, and queue arguments are immutable on the
 * broker: re-declaring a live queue with a different value fails startup with 406
 * PRECONDITION_FAILED. Deleting the consumer's old queues is part of deploying that change.
 *
 * <p>Admission rule for future elements: per-consumer, RabbitMQ-specific, developer-declared, with
 * a sane system default (prefetch would qualify). Connection settings, vhosts and serialization do
 * not belong here -- they are environment configuration.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RabbitPolicy {

    /**
     * Total broker deliveries before the message is parked in the DLQ -- the first attempt counts,
     * so {@code 1} means no delayed retries at all. Must be {@code >= 1}. {@code -1} (the default)
     * inherits the effective system default.
     */
    int maxAttempts() default -1;

    /**
     * How long a failed delivery waits in the {@code .retry} queue before returning to the main
     * queue. Must be {@code > 0} -- zero would mean instant redelivery, a hot loop through the
     * broker. {@code -1} (the default) inherits the effective system default.
     * See the class note before changing this against live queues.
     */
    long delayMillis() default -1;
}
