package io.github.siloverse.messaging.spring.outbox;

import java.time.Duration;
import java.util.Objects;

/**
 * Tuning for the outbox relay's polling loop. Optional: when the application provides no
 * bean of this type, the messaging lifecycle falls back to {@link #DEFAULT}.
 *
 * <p>{@code pollInterval} is the fixed DELAY between the end of one relay tick and the start
 * of the next ({@code scheduleWithFixedDelay} -- no overlapping ticks by construction), i.e.
 * the worst-case added latency between a committed transaction and its messages reaching the
 * broker.
 */
public record OutboxRelaySettings(Duration pollInterval) {

    public static final OutboxRelaySettings DEFAULT = new OutboxRelaySettings(Duration.ofSeconds(1));

    public OutboxRelaySettings {
        Objects.requireNonNull(pollInterval, "pollInterval must not be null");
        if (pollInterval.isZero() || pollInterval.isNegative()) {
            throw new IllegalArgumentException(
                    "pollInterval must be positive, was " + pollInterval
                            + ". Choose the fixed delay between relay ticks, e.g. Duration.ofSeconds(1).");
        }
    }
}
