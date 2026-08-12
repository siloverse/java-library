package io.github.siloverse.messaging.core.fixtures;

import io.github.siloverse.messaging.core.api.Command;
import io.github.siloverse.messaging.core.api.Event;

public record WeirdMessage(String id) implements Event, Command {
}
