package io.github.siloverse.messaging.core.fixtures;

import io.github.siloverse.messaging.core.api.Event;

public record OrderConfirmed(String id) implements Event {
}
