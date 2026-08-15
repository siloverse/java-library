package io.github.siloverse.messaging.spring.config;

import java.util.List;
import java.util.Objects;

import io.github.siloverse.messaging.spring.listener.MessageListener;
import io.github.siloverse.messaging.spring.outbox.OutboxRelay;
import io.github.siloverse.messaging.spring.outbox.OutboxRelaySettings;
import io.github.siloverse.messaging.spring.topology.TopologyDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Starts and stops the messaging machinery in the only order that is safe.
 *
 * <p>{@code start()} runs after the consumer registry is frozen (Spring finishes
 * {@code SmartInitializingSingleton} callbacks before starting lifecycle beans): first every
 * {@link TopologyDeclaration} declares exchanges, queues and bindings, then every
 * {@link MessageListener} starts consuming (queues must exist first), THEN the outbox relay is scheduled -- a relay
 * tick before declaration would publish into a missing exchange. {@code stop()} runs before singletons are destroyed:
 * listeners stop first, then the relay, so everything is quiet before the broker connection beans close.
 *
 * <p>The scheduler is library-owned (single thread, {@code outbox-relay-} prefix): created on
 * start, destroyed on stop -- close what you open. A failed relay tick is logged and the schedule continues: unstamped
 * rows are retried on the next tick, which IS the relay's retry mechanism.
 */
public class MessagingLifecycle implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(MessagingLifecycle.class);

    private final OutboxRelay relay;
    private final OutboxRelaySettings settings;
    private final List<TopologyDeclaration> topologyDeclarations;
    private final List<MessageListener> messageListeners;

    private ThreadPoolTaskScheduler scheduler;
    private volatile boolean running;

    public MessagingLifecycle(
            OutboxRelay relay,
            OutboxRelaySettings settings,
            List<TopologyDeclaration> topologyDeclarations,
            List<MessageListener> messageListeners
    ) {
        this.relay = Objects.requireNonNull(relay, "relay must not be null");
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.topologyDeclarations =
                List.copyOf(Objects.requireNonNull(topologyDeclarations, "topologyDeclarations must not be null"));
        this.messageListeners =
                List.copyOf(Objects.requireNonNull(messageListeners, "messageListeners must not be null"));
    }

    @Override
    public void start() {
        if (running) {
            return;
        }

        topologyDeclarations.forEach(TopologyDeclaration::declare);
        messageListeners.forEach(MessageListener::start);

        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("outbox-relay-");
        scheduler.initialize();
        scheduler.scheduleWithFixedDelay(this::relayTick, settings.pollInterval());

        running = true;
        logger.info("Messaging started: topology declared ({} declaration(s)), {} listener(s) consuming,"
                        + " outbox relay polling every {}",
                topologyDeclarations.size(), messageListeners.size(), settings.pollInterval());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        messageListeners.forEach(MessageListener::stop);
        scheduler.shutdown();
        scheduler = null;
        logger.info("Messaging stopped: listeners stopped, outbox relay cancelled");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void relayTick() {
        try {
            relay.pollOnce();
        } catch (RuntimeException e) {
            // the relay's retry mechanism IS the next tick: rows it failed to ship are
            // still unstamped and will be picked up again
            logger.warn("Outbox relay tick failed; unstamped rows will be retried next tick", e);
        }
    }
}
