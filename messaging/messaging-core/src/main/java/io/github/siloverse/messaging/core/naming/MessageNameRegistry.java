package io.github.siloverse.messaging.core.naming;

import io.github.siloverse.messaging.core.error.MessagingConfigurationException;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable map from message class to its wire name (the broker routing key).
 *
 * <p>Wire names are declared by a person, never derived from class names: a rename or move
 * of a class must never change what is on the wire. Convention for values: {@code <service>.<message>}, lowercase,
 * dash-separated -- e.g. {@code order-silo.order-created}.
 *
 * <p>Each service ships one registry INSIDE its messages (contract) jar, next to the message
 * records, so publisher and consumers read the same map:
 *
 * <pre>{@code
 * public final class OrderSiloMessages {
 *     public static MessageNameRegistry names() {
 *         return MessageNameRegistry.builder()
 *                 .register(OrderCreated.class, "order-silo.order-created")
 *                 .freeze();
 *     }
 * }
 * }</pre>
 */
public final class MessageNameRegistry {

    private final Map<Class<?>, String> names;

    private MessageNameRegistry(Map<Class<?>, String> names) {
        this.names = Map.copyOf(names);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MessageNameRegistry compose(MessageNameRegistry... messageNameRegistries) {
        var builder = MessageNameRegistry.builder();
        Arrays.stream(messageNameRegistries).forEach(r -> r.names.forEach(builder::register));
        return builder.freeze();
    }

    public String nameOf(Class<?> messageClass) {
        Objects.requireNonNull(messageClass, "messageClass must not be null");
        String name = names.get(messageClass);
        if (name == null) {
            throw new MessagingConfigurationException(
                    "No wire name registered for message class " + messageClass.getName()
                            + ". Add register(" + messageClass.getSimpleName() + ".class, \"<service>.<message>\") "
                            + "to the MessageNameRegistry in its messages module.");
        }
        return name;
    }

    public List<String> allNames() {
        return this.names.values().stream().toList();
    }

    public static final class Builder {

        private final Map<Class<?>, String> names = new HashMap<>();

        private final Map<String, Class<?>> wireNames = new HashMap<>();

        private Builder() {
        }

        public Builder register(Class<?> messageClass, String wireName) {
            Objects.requireNonNull(messageClass, "messageClass must not be null");
            Objects.requireNonNull(wireName, "wireName must not be null");

            if (wireName.isBlank()) {
                throw new MessagingConfigurationException(
                        "Wire name for message class " + messageClass.getName() + " is blank. "
                                + "Provide a non-blank name following <service>.<message>, "
                                + "e.g. \"order-silo.order-created\".");
            }
            if (wireNames.containsKey(wireName)) {
                throw new MessagingConfigurationException(
                        "Wire name '" + wireName + "' is already used by message class " +
                                wireNames.get(wireName).getName()
                                + " and cannot be reused for " + messageClass.getName() + ". "
                                + "A wire name is the routing key -- two classes sharing it would be "
                                + "indistinguishable on the wire. Pick a distinct name for "
                                + messageClass.getName() + ".");
            }
            String existing = names.putIfAbsent(messageClass, wireName);
            if (existing != null) {
                throw new MessagingConfigurationException(
                        "Message class " + messageClass.getName() + " is already registered "
                                + "under wire name '" + existing + "'. Each message class has exactly one "
                                + "wire name -- remove the duplicate register(...) call.");
            }
            wireNames.put(wireName, messageClass);
            return this;
        }

        public MessageNameRegistry freeze() {
            return new MessageNameRegistry(names);
        }
    }
}
