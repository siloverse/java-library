package io.github.siloverse.messaging.api;

/**
 * A statement that something has happened.
 *
 * <p>An event type may be consumed by zero, one or many consumer methods.
 */
public interface Event extends Message {
}
