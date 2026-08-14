package io.github.siloverse.messaging.core.transport;

/**
 * Puts an envelope on the wire, synchronously and honestly.
 *
 * <p>Contract: {@code send} returns normally only after the broker has durably accepted
 * the envelope (for RabbitMQ: publisher confirm received). If the broker rejects it,
 * times out, or is unreachable, {@code send} throws
 * {@link io.github.siloverse.messaging.core.error.MessagingException} -- it must never
 * return before acceptance, because callers treat a normal return as permission to
 * record the message as published (the relay stamps {@code published_at} on it;
 * the direct bus reports success to its caller).
 */
public interface MessageTransport {
    void send(Envelope envelope);
}
