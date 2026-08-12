package io.github.siloverse.messaging.core.api;

import io.github.siloverse.messaging.core.error.MessagingConfigurationException;

/**
 * The channel semantic of a message class: fan-out, single-consumer. Derived from the marker
 * interface, never declared -- so the two can never disagree.
 */
public enum MessageKind {

    EVENT,      // Publish-Subscribe: 0..many consumers
    COMMAND;    // Point-to-Point: exactly one, fire-and-forget

    /**
     * Classifies a message class, rejecting classes that implement zero or multiple markers.
     *
     * @throws MessagingConfigurationException if the class has no coherent channel semantic
     */
    public static MessageKind of(Class<?> messageClass) {
        boolean isEvent = Event.class.isAssignableFrom(messageClass);
        boolean isCommand = Command.class.isAssignableFrom(messageClass);

        int markers = (isEvent ? 1 : 0) + (isCommand ? 1 : 0);
        if (markers > 1) {
            throw new MessagingConfigurationException(messageClass.getName()
                    + " implements more than one of Event, Command. A message has exactly "
                    + "one channel semantic: fan-out (Event), single-consumer (Command)");
        }
        if (markers == 0) {
            throw new MessagingConfigurationException(
                    "Unsupported message type '" + messageClass.getName()
                            + "'. Messages must implement either Event or Command.");
        }
        return isEvent ? EVENT : COMMAND;
    }
}