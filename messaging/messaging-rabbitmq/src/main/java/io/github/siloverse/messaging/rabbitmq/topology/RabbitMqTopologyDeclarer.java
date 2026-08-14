package io.github.siloverse.messaging.rabbitmq.topology;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.siloverse.messaging.core.consumer.ConsumerRegistry;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.naming.MessageNameRegistry;

/**
 * Declares broker topology from the frozen registries at startup.
 *
 * <p>Each side declares what it touches: publishers declare one durable fanout exchange per
 * wire name (so a legal zero-consumer publish is a clean no-op instead of a 404); consumers
 * additionally declare their queue {@code <service>-<consumer-id>} and bind it to the
 * exchange. Declarations are idempotent -- both sides declaring the same exchange, and
 * restarts re-declaring everything, are no-ops by AMQP contract.
 *
 * <p>Failure tiers: an exchange already declared with different settings (406
 * precondition-failed) or a consumed class missing from the name registry is a
 * {@link MessagingConfigurationException}; broker/connection trouble is a plain
 * {@link MessagingException}. Both abort startup.
 */
public class RabbitMqTopologyDeclarer {

    private final Connection connection;

    public RabbitMqTopologyDeclarer(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
    }

    public void declarePublisherTopology(MessageNameRegistry names) {
        Objects.requireNonNull(names, "names must not be null");

        try (Channel channel = openChannel()) {
            for (String wireName : names.allNames()) {
                declareExchange(channel, wireName);
            }
        } catch (IOException | TimeoutException e) {
            throw closingFailure(e);
        }
    }

    public void declareConsumerTopology(String serviceName, ConsumerRegistry consumers, MessageNameRegistry names) {
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(consumers, "consumers must not be null");
        Objects.requireNonNull(names, "names must not be null");
        if (serviceName.isBlank()) {
            throw new IllegalArgumentException("serviceName must not be blank -- it prefixes every queue name");
        }

        try (Channel channel = openChannel()) {
            for (var consumer : consumers.allConsumers()) {
                String wireName = names.nameOf(consumer.messageClass());
                String queue = serviceName + "-" + consumer.id();

                declareExchange(channel, wireName);
                declareQueue(channel, queue);
                bind(channel, queue, wireName);
            }
        } catch (IOException | TimeoutException e) {
            throw closingFailure(e);
        }
    }

    private Channel openChannel() {
        try {
            return connection.createChannel();
        } catch (IOException | ShutdownSignalException e) {
            throw new MessagingException(
                    "Could not open a channel to declare topology: the connection is closed or the"
                            + " broker refused a channel. Verify the broker is reachable and the"
                            + " connection is still open.", e);
        }
    }

    private static void declareExchange(Channel channel, String wireName) {
        try {
            channel.exchangeDeclare(wireName, BuiltinExchangeType.FANOUT, true);
        } catch (IOException e) {
            throw declarationFailure("exchange '" + wireName + "'", e);
        }
    }

    private static void declareQueue(Channel channel, String queue) {
        try {
            channel.queueDeclare(queue, true, false, false, null);
        } catch (IOException e) {
            throw declarationFailure("queue '" + queue + "'", e);
        }
    }

    private static void bind(Channel channel, String queue, String wireName) {
        try {
            channel.queueBind(queue, wireName, wireName);
        } catch (IOException e) {
            throw declarationFailure("binding of queue '" + queue + "' to exchange '" + wireName + "'", e);
        }
    }

    private static MessagingException closingFailure(Exception e) {
        return new MessagingException(
                "Topology was declared, but the declaration channel failed to close cleanly."
                        + " Verify the broker connection is healthy.", e);
    }

    private static MessagingException declarationFailure(String subject, IOException e) {
        if (isPreconditionFailed(e)) {
            return new MessagingConfigurationException(
                    "Declaring " + subject + " failed: it already exists on the broker with"
                            + " different settings (406 precondition-failed). Every declarer must"
                            + " use identical settings -- align this service's topology with the"
                            + " existing declaration. Broker said: " + e.getCause().getMessage(), e);
        }
        return new MessagingException(
                "Could not declare " + subject + ". Verify the broker is reachable and healthy.", e);
    }

    private static boolean isPreconditionFailed(IOException e) {
        return e.getCause() instanceof ShutdownSignalException shutdown
                && shutdown.getReason() instanceof AMQP.Channel.Close close
                && close.getReplyCode() == AMQP.PRECONDITION_FAILED;
    }
}
