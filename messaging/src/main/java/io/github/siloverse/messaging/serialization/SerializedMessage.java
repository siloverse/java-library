package io.github.siloverse.messaging.serialization;

/**
 * A message reduced to the two values that are stored.
 *
 * @param type    the type token used to reconstruct the message later
 * @param payload the serialized state
 */
public record SerializedMessage(String type, String payload) {
}
