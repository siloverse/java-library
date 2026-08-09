package io.github.siloverse.messaging.api;

/**
 * An instruction to perform exactly one action.
 *
 * <p>A command type must be handled by exactly one consumer method.
 */
public interface Command extends Message {
}
