package io.github.siloverse.messaging.api;

/**
 * Marker interface for anything that can travel through a bus.
 *
 * <p>Implementations are expected to be immutable value objects (typically records) that Jackson
 * can serialize and deserialize, because durable messages are stored as JSON.
 */
public interface Message {
}
