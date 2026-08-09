package io.github.siloverse.messaging.fixture;

import io.github.siloverse.messaging.api.Event;

import java.util.UUID;

/**
 * Example event used across the test suite.
 */
public record OrderConfirmed(UUID orderId) implements Event {
}
