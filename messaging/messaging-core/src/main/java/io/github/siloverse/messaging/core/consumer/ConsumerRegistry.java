package io.github.siloverse.messaging.core.consumer;

import io.github.siloverse.messaging.core.api.*;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.error.NoHandlerException;

import java.util.*;
import java.util.stream.Stream;

public class ConsumerRegistry {

    /**
     * Lifecycle of the registry. Write-only while being built, read-only once frozen:
     * lookups throw until FROZEN, registrations throw after it. {@code freeze()} is the
     * explicit construction-complete signal in every usage, plain Java and unit tests
     * included.
     *
     * <p>OPEN: registrations work, lookups THROW -- a registry still being built may be
     * missing consumers registered later, so reading it would silently skip them.
     *
     * <p>INITIALIZING: behaviorally identical to OPEN. The marker a lifecycle manager
     * (the Spring initializer) sets via beginInitialization() to make scanning-underway
     * explicit and to reject re-initialization of a frozen registry.
     *
     * <p>FROZEN: lookups work, registrations THROW. Topology is final; broker
     * adapters may read it, listener threads may share it (no further mutation means the plain HashMaps are safely
     * publishable).
     */
    private enum State {
        OPEN,
        INITIALIZING,
        FROZEN
    }

    private volatile State state = State.OPEN;

    private final Map<Class<?>, List<ConsumerDefinition>> eventConsumers = new HashMap<>();
    private final Map<Class<?>, ConsumerDefinition> commandConsumers = new HashMap<>();
    private final Set<String> allHandlers = new HashSet<>();

    /** Called by a lifecycle manager before scanning begins. */
    public void beginInitialization() {
        if (state == State.FROZEN) {
            throw new MessagingException("Registry is already frozen; cannot re-initialize.");
        }
        state = State.INITIALIZING;
    }

    /** Called after scanning completes. Idempotent by design: freeze-of-frozen is a no-op. */
    public void freeze() {
        state = State.FROZEN;
    }

    public boolean isFrozen() {
        return state == State.FROZEN;
    }

    public void register(ConsumerDefinition def) {

        if (isFrozen()) {
            throw new MessagingException(
                    "Registry is frozen -- cannot register consumer '" + def.id() + "'. "
                            + "Topology must not change after startup: broker queues and listeners "
                            + "are derived from it. Register all consumers before initialization completes.");
        }

        checkConsumerIdNotRegistered(def);
        var kind = MessageKind.of(def.messageClass());

        switch (kind) {
            case EVENT -> eventConsumers.computeIfAbsent(def.messageClass(), ignored -> new ArrayList<>()).add(def);
            case COMMAND -> {
                ConsumerDefinition existing = commandConsumers.putIfAbsent(def.messageClass(), def);
                if (existing != null) {
                    throw new MessagingConfigurationException(
                            String.format("Multiple consumers registered for command: %s",
                                    def.messageClass().getName()));
                }
            }
        }
        allHandlers.add(def.id());
    }

    private void checkConsumerIdNotRegistered(ConsumerDefinition def) {
        if (allHandlers.contains(def.id())) {
            throw new MessagingConfigurationException(String.format("""
                    Consumer id is already registered,
                    multiple consumers registration with same id is not allowed: id[%s],
                    message[%s]""", def.id(), def.messageClass().getName()));
        }
    }

    public List<ConsumerDefinition> eventConsumersFor(Class<?> eventType) {
        assertLookupAllowed();
        return List.copyOf(eventConsumers.getOrDefault(eventType, Collections.emptyList()));
    }

    public ConsumerDefinition commandConsumerFor(Class<?> commandType) {
        assertLookupAllowed();
        ConsumerDefinition def = commandConsumers.get(commandType);
        if (def == null) {
            throw new NoHandlerException("No consumer registered for command " + commandType.getName() +
                    ". A Command requires exactly one consumer -- annotate a method " + "with @Consumer taking a " +
                    commandType.getSimpleName() + " parameter.");
        }
        return def;
    }

    public List<ConsumerDefinition> allConsumers() {
        assertLookupAllowed();

        return Stream.of(
                        commandConsumers.values().stream().toList(),
                        eventConsumers.values().stream().flatMap(List::stream).toList()
                ).flatMap(List::stream)
                .toList();
    }

    private void assertLookupAllowed() {
        if (state != State.FROZEN) {
            throw new MessagingException(
                    "Registry is not frozen -- lookups are allowed only after construction completes. "
                            + "A registry still open or initializing may be missing consumers registered "
                            + "later, so dispatch or topology reads would silently skip them. Freeze the "
                            + "registry after the last registration (the Spring initializer freezes after "
                            + "scanning; plain-Java use calls freeze() explicitly). Publishing from "
                            + "@PostConstruct or during bean creation is not supported -- publish from an "
                            + "ApplicationRunner or after startup instead.");
        }
    }
}
