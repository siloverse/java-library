package io.github.siloverse.messaging.rabbitmq.topology;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;

public final class QueueNames {

    private QueueNames() {
    }

    private static final String MAIN_QUEUE_PATTERN = "%s-%s";

    private static final String RETRY_QUEUE_PATTERN = "%s.retry";

    private static final String DLQ_PATTERN = "%s.dlq";

    public static String mainQueueName(String serviceName, ConsumerDefinition consumerDefinition) {
        return MAIN_QUEUE_PATTERN.formatted(
                serviceName,
                consumerDefinition.id()
        );
    }

    public static String retryQueueName(String serviceName, ConsumerDefinition consumerDefinition) {
        return RETRY_QUEUE_PATTERN.formatted(
                mainQueueName(serviceName, consumerDefinition)
        );
    }

    public static String dlqName(String serviceName, ConsumerDefinition consumerDefinition) {
        return DLQ_PATTERN.formatted(
                mainQueueName(serviceName, consumerDefinition)
        );
    }
}
