package io.github.siloverse.messaging.fixture;

import io.github.siloverse.messaging.api.Command;

import java.util.UUID;

/**
 * Example command used across the test suite.
 */
public record ConfirmOrder(UUID orderId) implements Command {
}
