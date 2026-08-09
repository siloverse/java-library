package io.github.siloverse.messaging.consumer;

import io.github.siloverse.messaging.api.Command;
import io.github.siloverse.messaging.api.Event;
import io.github.siloverse.messaging.api.MessageKind;
import io.github.siloverse.messaging.exception.ConsumerDefinitionException;
import io.github.siloverse.messaging.exception.NoConsumerForCommandException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps message types to the consumers that handle them.
 *
 * <p>The registry is populated once, after all singletons have been created, and is read only
 * afterwards. Matching is on the concrete message type: a consumer declared for a supertype does
 * not receive subtypes.
 */
public class ConsumerRegistry implements SmartInitializingSingleton {

    private static final Log logger = LogFactory.getLog(ConsumerRegistry.class);

    private final ConsumerScanner scanner;
    private final ConfigurableListableBeanFactory beanFactory;

    private volatile Map<Class<?>, ConsumerDescriptor> commandConsumers = Map.of();
    private volatile Map<Class<?>, List<ConsumerDescriptor>> eventConsumers = Map.of();
    private volatile Map<String, ConsumerDescriptor> consumersById = Map.of();

    public ConsumerRegistry(ConsumerScanner scanner, ConfigurableListableBeanFactory beanFactory) {
        this.scanner = scanner;
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        register(scanner.scan(beanFactory));
    }

    /**
     * Replaces the registry content, validating command cardinality.
     *
     * @param descriptors the consumers to register
     * @throws ConsumerDefinitionException if a command type has more than one consumer
     */
    public void register(Collection<ConsumerDescriptor> descriptors) {
        Map<Class<?>, ConsumerDescriptor> commands = new HashMap<>();
        Map<Class<?>, List<ConsumerDescriptor>> events = new HashMap<>();
        Map<String, ConsumerDescriptor> byId = new LinkedHashMap<>();

        for (ConsumerDescriptor descriptor : descriptors) {
            ConsumerDescriptor clash = byId.put(descriptor.consumerId(), descriptor);
            if (clash != null) {
                throw new ConsumerDefinitionException(
                        "Two consumers resolve to the same id '" + descriptor.consumerId() + "': "
                                + clash.describe() + " and " + descriptor.describe() + ".");
            }
            if (descriptor.kind() == MessageKind.COMMAND) {
                ConsumerDescriptor existing = commands.putIfAbsent(descriptor.messageType(), descriptor);
                if (existing != null) {
                    throw new ConsumerDefinitionException(
                            "Command " + descriptor.messageType().getName() + " must have exactly one "
                                    + "@Consumer method but has at least two: " + existing.describe()
                                    + " and " + descriptor.describe() + ".");
                }
            }
            else {
                events.computeIfAbsent(descriptor.messageType(), type -> new ArrayList<>()).add(descriptor);
            }
        }

        Comparator<ConsumerDescriptor> ordering =
                Comparator.comparingInt(ConsumerDescriptor::order).thenComparing(ConsumerDescriptor::consumerId);
        events.replaceAll((type, list) -> list.stream().sorted(ordering).toList());

        this.commandConsumers = Map.copyOf(commands);
        this.eventConsumers = Map.copyOf(events);
        this.consumersById = Map.copyOf(byId);

        if (logger.isDebugEnabled()) {
            logger.debug("Registered " + byId.size() + " @Consumer methods: "
                    + commands.size() + " command consumer(s), "
                    + events.values().stream().mapToInt(List::size).sum() + " event consumer(s)");
        }
    }

    /**
     * @param commandType the concrete command type
     * @return the single consumer for that command, if one is registered
     */
    public Optional<ConsumerDescriptor> findCommandConsumer(Class<? extends Command> commandType) {
        return Optional.ofNullable(commandConsumers.get(commandType));
    }

    /**
     * @param commandType the concrete command type
     * @return the single consumer for that command
     * @throws NoConsumerForCommandException if no consumer is registered
     */
    public ConsumerDescriptor requireCommandConsumer(Class<? extends Command> commandType) {
        ConsumerDescriptor descriptor = commandConsumers.get(commandType);
        if (descriptor == null) {
            throw new NoConsumerForCommandException(commandType);
        }
        return descriptor;
    }

    /**
     * @param eventType the concrete event type
     * @return every consumer of that event, ordered, possibly empty
     */
    public List<ConsumerDescriptor> findEventConsumers(Class<? extends Event> eventType) {
        return eventConsumers.getOrDefault(eventType, List.of());
    }

    /**
     * Resolves the consumer a stored delivery was created for.
     *
     * @param consumerId the persisted consumer id
     * @return the matching consumer, if it still exists in this application
     */
    public Optional<ConsumerDescriptor> findById(String consumerId) {
        return Optional.ofNullable(consumersById.get(consumerId));
    }
}
