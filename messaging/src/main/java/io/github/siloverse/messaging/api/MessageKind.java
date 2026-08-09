package io.github.siloverse.messaging.api;

/**
 * Distinguishes the two delivery semantics supported by the library.
 */
public enum MessageKind {

    /** Exactly one consumer. */
    COMMAND,

    /** Zero, one or many consumers, each delivered and retried independently. */
    EVENT;

    /**
     * Classifies a message type.
     *
     * @param messageType the concrete message type
     * @return the kind, or {@code null} when the type is neither a {@link Command} nor an
     *         {@link Event}, or is both
     */
    public static MessageKind of(Class<?> messageType) {
        boolean command = Command.class.isAssignableFrom(messageType);
        boolean event = Event.class.isAssignableFrom(messageType);
        if (command == event) {
            return null;
        }
        return command ? COMMAND : EVENT;
    }
}
