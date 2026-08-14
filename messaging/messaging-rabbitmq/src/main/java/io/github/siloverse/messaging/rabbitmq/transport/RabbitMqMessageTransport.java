package io.github.siloverse.messaging.rabbitmq.transport;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownSignalException;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.MessageTransport;

/**
 * {@link MessageTransport} over a RabbitMQ publisher-confirm channel.
 *
 * <p>Publishes to the exchange named after the envelope's message type (routing key carries
 * the same value as wire self-description). Owns a single confirm-mode channel on the given
 * connection; the connection itself is borrowed, never closed. Thread-safe: concurrent
 * senders pipeline on the shared channel, each blocking only on its own confirm.
 */
public class RabbitMqMessageTransport implements MessageTransport {

    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(30);
    private static final int PERSISTENT_DELIVERY_MODE = 2;

    private final Channel channel;
    private final Object publishLock = new Object();

    private final ConcurrentNavigableMap<Long, PendingMessage> outstanding = new ConcurrentSkipListMap<>();

    public RabbitMqMessageTransport(Connection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        try {
            channel = connection.createChannel();
            channel.confirmSelect();
        } catch (IOException | ShutdownSignalException e) {
            throw new MessagingException(
                    "Could not open a publisher-confirm channel: the connection is closed or the"
                            + " broker refused a channel. Verify the broker is reachable and the"
                            + " connection is still open.", e);
        }
        channel.addConfirmListener(this::handleAck, this::handleNack);
        channel.addShutdownListener(this::failAllOutstanding);
    }

    @Override
    public void send(Envelope envelope) {
        awaitConfirm(envelope, publish(envelope));
    }

    private CompletableFuture<Void> publish(Envelope envelope) {
        var confirmation = new CompletableFuture<Void>();
        // sequence number, registration and publish must be atomic together: another
        // publish in between would misalign the broker's numbering with `outstanding`
        synchronized (publishLock) {
            long sequence = channel.getNextPublishSeqNo();
            outstanding.put(sequence, new PendingMessage(envelope, confirmation));
            try {
                channel.basicPublish(
                        envelope.messageType(),
                        envelope.messageType(),
                        propertiesOf(envelope),
                        envelope.payload()
                );
            } catch (IOException | ShutdownSignalException e) {
                outstanding.remove(sequence);
                throw new MessagingException(
                        describe(envelope) + " could not be handed to the broker."
                                + " The message is not published; retry once the broker is reachable.", e);
            }
        }
        return confirmation;
    }

    private void awaitConfirm(Envelope envelope, CompletableFuture<Void> confirmation) {
        try {
            confirmation.get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof MessagingException failure
                    ? failure
                    : new MessagingException(describe(envelope) + " failed to publish.", e.getCause());
        } catch (TimeoutException e) {
            throw new MessagingException(
                    describe(envelope) + " got no broker confirm within " + CONFIRM_TIMEOUT.toSeconds()
                            + "s. Publish outcome is unknown -- treat as unpublished and retry"
                            + " (consumers dedup by message id).", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MessagingException(
                    describe(envelope) + " was interrupted while waiting for the broker confirm."
                            + " Publish outcome is unknown -- treat as unpublished and retry"
                            + " (consumers dedup by message id).", e);
        }
    }

    private void handleAck(long sequence, boolean multiple) {
        drainUpTo(sequence, multiple, pending -> pending.confirmation().complete(null));
    }

    private void handleNack(long sequence, boolean multiple) {
        drainUpTo(sequence, multiple, pending -> pending.confirmation().completeExceptionally(
                new MessagingException(
                        describe(pending.envelope()) + " was refused by the broker (basic.nack)."
                                + " The message is not published; retry it.")));
    }

    private void drainUpTo(long sequence, boolean multiple, Consumer<PendingMessage> outcome) {
        if (!multiple) {
            var pending = outstanding.remove(sequence);
            if (pending != null) {
                outcome.accept(pending);
            }
            return;
        }
        var confirmed = outstanding.headMap(sequence, true);
        Map.Entry<Long, PendingMessage> entry;
        while ((entry = confirmed.pollFirstEntry()) != null) {
            outcome.accept(entry.getValue());
        }
    }

    private void failAllOutstanding(ShutdownSignalException reason) {
        Map.Entry<Long, PendingMessage> entry;
        while ((entry = outstanding.pollFirstEntry()) != null) {
            var pending = entry.getValue();
            pending.confirmation().completeExceptionally(new MessagingException(
                    describe(pending.envelope()) + " lost its channel before the broker confirmed."
                            + " Publish outcome is unknown -- treat as unpublished and retry"
                            + " (consumers dedup by message id).", reason));
        }
    }

    private static AMQP.BasicProperties propertiesOf(Envelope envelope) {
        return new AMQP.BasicProperties.Builder()
                .messageId(envelope.messageId().toString())
                .contentType(envelope.headers().get("content-type"))
                .deliveryMode(PERSISTENT_DELIVERY_MODE)
                .headers(Map.copyOf(envelope.headers()))
                .build();
    }

    private static String describe(Envelope envelope) {
        return "Message " + envelope.messageId() + " of type '" + envelope.messageType() + "'";
    }

    private record PendingMessage(Envelope envelope, CompletableFuture<Void> confirmation) {
    }
}
