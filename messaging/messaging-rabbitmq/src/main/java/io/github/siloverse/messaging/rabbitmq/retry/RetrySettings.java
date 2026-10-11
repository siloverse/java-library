package io.github.siloverse.messaging.rabbitmq.retry;

import java.time.Duration;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.rabbitmq.consumer.RabbitPolicy;

/**
 * Holds the effective default retry policy; {@link #policyFor} lets a consumer's {@link RabbitPolicy} overrule it per
 * field -- annotation > configured default > {@link #DEFAULT}.
 */
public record RetrySettings(RetryPolicy defaultPolicy) {

    public static final RetrySettings DEFAULT =
            new RetrySettings(new RetryPolicy(3, Duration.ofSeconds(10)));

    public RetryPolicy policyFor(ConsumerDefinition definition) {
        RabbitPolicy annotation = definition.method().getAnnotation(RabbitPolicy.class);
        if (annotation == null) {
            return defaultPolicy;
        }

        int attempts = annotation.maxAttempts() == -1 ? defaultPolicy.maxAttempts()
                : annotation.maxAttempts();
        Duration delay =
                annotation.delayMillis() == -1 ? defaultPolicy.delay() : Duration.ofMillis(annotation.delayMillis());

        // RetryPolicy owns what "valid" means; this only adds the culprit and the right tier
        try {
            return new RetryPolicy(attempts, delay);
        } catch (IllegalArgumentException invalidPolicy) {
            throw new MessagingConfigurationException(
                    "Consumer '" + definition.id() + "' declares an invalid @RabbitPolicy: "
                            + invalidPolicy.getMessage(), invalidPolicy);
        }
    }
}
