package io.github.siloverse.messaging.async;

import io.github.siloverse.messaging.persistence.DeliveryClaimStrategy;
import io.github.siloverse.messaging.persistence.repository.MessageDeliveryRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Moves durable deliveries from the database to the worker pool.
 *
 * <p>The database is the queue. Every cycle the poller first returns abandoned deliveries to the
 * queue, then claims a batch and hands each claimed id to the {@link TaskExecutor}. The executor is
 * only the execution mechanism: if the process dies, every claimed delivery is still in the
 * database and is picked up again once its lock expires.
 *
 * <p>Runs its own single threaded scheduler tied to the application lifecycle, so applications do
 * not need {@code @EnableScheduling}.
 */
public class MessagePoller implements SmartLifecycle {

    private static final Log logger = LogFactory.getLog(MessagePoller.class);

    private final DeliveryClaimStrategy claimStrategy;
    private final MessageDeliveryRepository deliveries;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor taskExecutor;
    private final MessageProcessor processor;
    private final Clock clock;

    private final boolean autoStartup;
    private final Duration pollInterval;
    private final Duration initialDelay;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration lockTimeout;

    private final AtomicInteger threadCounter = new AtomicInteger();

    private volatile ScheduledExecutorService scheduler;

    public MessagePoller(DeliveryClaimStrategy claimStrategy,
                         MessageDeliveryRepository deliveries,
                         TransactionTemplate transactionTemplate,
                         TaskExecutor taskExecutor,
                         MessageProcessor processor,
                         Clock clock,
                         boolean autoStartup,
                         Duration pollInterval,
                         Duration initialDelay,
                         int batchSize,
                         int maxAttempts,
                         Duration retryDelay,
                         Duration lockTimeout) {
        this.claimStrategy = claimStrategy;
        this.deliveries = deliveries;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
        this.processor = processor;
        this.clock = clock;
        this.autoStartup = autoStartup;
        this.pollInterval = pollInterval;
        this.initialDelay = initialDelay;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
        this.lockTimeout = lockTimeout;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }

    @Override
    public synchronized void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "messaging-poller-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(
                this::pollQuietly,
                initialDelay.toMillis(),
                Math.max(1, pollInterval.toMillis()),
                TimeUnit.MILLISECONDS);
        logger.info("Message poller started, polling every " + pollInterval);
    }

    @Override
    public synchronized void stop() {
        ScheduledExecutorService current = scheduler;
        scheduler = null;
        if (current != null) {
            current.shutdownNow();
            logger.info("Message poller stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return scheduler != null;
    }

    @Override
    public int getPhase() {
        // Stop before the rest of the application, so no new work is claimed during shutdown.
        return SmartLifecycle.DEFAULT_PHASE - 100;
    }

    /**
     * Runs one poll cycle. Exposed so tests, and applications that want their own scheduling, can
     * drive the poller deterministically.
     *
     * @return the number of deliveries handed to the worker pool
     */
    public int pollOnce() {
        recoverAbandoned();

        List<UUID> claimed = transactionTemplate.execute(
                status -> claimStrategy.claim(batchSize, clock.instant(), maxAttempts));
        if (claimed == null || claimed.isEmpty()) {
            return 0;
        }

        int submitted = 0;
        for (UUID deliveryId : claimed) {
            try {
                taskExecutor.execute(() -> processor.process(deliveryId));
                submitted++;
            }
            catch (TaskRejectedException ex) {
                logger.debug("Worker pool is saturated, releasing delivery " + deliveryId);
                releaseClaim(deliveryId);
            }
        }
        return submitted;
    }

    /**
     * Returns deliveries whose worker never finished to the queue, and gives up on those that have
     * no attempts left. A delivery counts as abandoned once it has been {@code PROCESSING} for
     * longer than the configured lock timeout, which is how a delivery survives a JVM crash.
     *
     * @return the number of deliveries rescheduled or failed
     */
    public int recoverAbandoned() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(lockTimeout);
        Integer recovered = transactionTemplate.execute(status -> {
            int failed = deliveries.failAbandoned(cutoff, maxAttempts,
                    now, "Abandoned while PROCESSING and out of attempts (lock expired)");
            int rescheduled = deliveries.rescheduleAbandoned(cutoff, maxAttempts,
                    now.plus(retryDelay), "Abandoned while PROCESSING (lock expired), rescheduled");
            return failed + rescheduled;
        });
        if (recovered != null && recovered > 0) {
            logger.warn("Recovered " + recovered + " delivery(ies) abandoned before " + cutoff);
        }
        return recovered == null ? 0 : recovered;
    }

    private void releaseClaim(UUID deliveryId) {
        try {
            transactionTemplate.executeWithoutResult(status -> deliveries.findById(deliveryId)
                    .ifPresent(delivery -> {
                        delivery.releaseClaim();
                        deliveries.save(delivery);
                    }));
        }
        catch (RuntimeException ex) {
            logger.warn("Could not release delivery " + deliveryId
                    + ", it will be reclaimed once its lock expires", ex);
        }
    }

    private void pollQuietly() {
        try {
            pollOnce();
        }
        catch (Exception ex) {
            // Never let a failure kill the scheduled task; the next cycle tries again.
            logger.error("Poll cycle failed", ex);
        }
    }
}
