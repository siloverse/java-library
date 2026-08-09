package io.github.siloverse.messaging.fixture;

import io.github.siloverse.messaging.api.Event;

import java.util.UUID;

/**
 * Event without any consumer, used to check that publishing is still allowed.
 */
public record OrderCancelled(UUID orderId) implements Event {
}
