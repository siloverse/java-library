package io.github.siloverse.messaging.core.fixtures;

import io.github.siloverse.messaging.core.api.Command;

public record ConfirmOrder(String id) implements Command {
}
