package io.github.siloverse.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the messaging library, under the {@code messaging} prefix.
 */
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    private final Bus command = new Bus();
    private final Bus event = new Bus();
    private final Async async = new Async();
    private final Schema schema = new Schema();

    public Bus getCommand() {
        return command;
    }

    public Bus getEvent() {
        return event;
    }

    public Async getAsync() {
        return async;
    }

    public Schema getSchema() {
        return schema;
    }

    /**
     * How a bus delivers its messages.
     */
    public enum BusMode {

        /** Dispatch on the caller's thread, without touching the database. */
        SYNC,

        /** Store durably, opening a transaction if the caller has none, then deliver in the background. */
        ASYNC,

        /** Store durably inside the caller's transaction, which is mandatory, then deliver in the background. */
        TRANSACTIONAL_ASYNC
    }

    /**
     * Settings of one bus.
     */
    public static class Bus {

        /**
         * Which implementation is exposed as the {@code CommandBus} / {@code EventBus} bean.
         */
        private BusMode mode = BusMode.SYNC;

        public BusMode getMode() {
            return mode;
        }

        public void setMode(BusMode mode) {
            this.mode = mode;
        }
    }

    /**
     * Settings of the durable asynchronous pipeline.
     */
    public static class Async {

        /**
         * Whether durable messaging is available at all. When false, no message tables, poller or
         * durable buses are configured.
         */
        private boolean enabled = true;

        /**
         * Whether the poller starts with the application. Turn off to drive it yourself.
         */
        private boolean pollerEnabled = true;

        /**
         * How long to wait between poll cycles.
         */
        private Duration pollInterval = Duration.ofMillis(250);

        /**
         * How long to wait before the first poll cycle, giving the application time to finish
         * starting.
         */
        private Duration initialDelay = Duration.ofSeconds(1);

        /**
         * How many deliveries a single poll cycle claims at most.
         */
        private int batchSize = 100;

        /**
         * How often a delivery is attempted before it is marked FAILED.
         */
        private int maxAttempts = 5;

        /**
         * How long a failed delivery waits before becoming eligible again.
         */
        private Duration retryDelay = Duration.ofSeconds(5);

        /**
         * How long a delivery may stay PROCESSING before it is treated as abandoned, for example
         * because the JVM it was running in died. Must comfortably exceed the slowest consumer.
         */
        private Duration lockTimeout = Duration.ofMinutes(5);

        /**
         * How many deliveries are processed concurrently.
         */
        private int workerCount = 4;

        /**
         * How many claimed deliveries may wait in front of the workers.
         */
        private int queueCapacity = 1000;

        /**
         * How long shutdown waits for in flight deliveries to finish.
         */
        private Duration shutdownTimeout = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isPollerEnabled() {
            return pollerEnabled;
        }

        public void setPollerEnabled(boolean pollerEnabled) {
            this.pollerEnabled = pollerEnabled;
        }

        public Duration getPollInterval() {
            return pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getRetryDelay() {
            return retryDelay;
        }

        public void setRetryDelay(Duration retryDelay) {
            this.retryDelay = retryDelay;
        }

        public Duration getLockTimeout() {
            return lockTimeout;
        }

        public void setLockTimeout(Duration lockTimeout) {
            this.lockTimeout = lockTimeout;
        }

        public int getWorkerCount() {
            return workerCount;
        }

        public void setWorkerCount(int workerCount) {
            this.workerCount = workerCount;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public Duration getShutdownTimeout() {
            return shutdownTimeout;
        }

        public void setShutdownTimeout(Duration shutdownTimeout) {
            this.shutdownTimeout = shutdownTimeout;
        }
    }

    /**
     * Settings of the optional schema initializer.
     */
    public static class Schema {

        /**
         * Whether to create the message tables on startup. Off by default: production schemas
         * normally belong to Flyway or Liquibase.
         */
        private boolean initialize = false;

        /**
         * Location of the DDL script used when initialization is enabled.
         */
        private String location = "classpath:io/github/siloverse/messaging/schema-postgresql.sql";

        public boolean isInitialize() {
            return initialize;
        }

        public void setInitialize(boolean initialize) {
            this.initialize = initialize;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }
}
