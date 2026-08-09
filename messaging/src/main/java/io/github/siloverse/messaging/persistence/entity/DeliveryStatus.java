package io.github.siloverse.messaging.persistence.entity;

/**
 * Lifecycle of a single delivery row.
 *
 * <pre>
 * PENDING ──claim──&gt; PROCESSING ──consumer returns──&gt; PROCESSED
 *    ^                    │
 *    │                    ├── consumer throws, attempts left ──&gt; PENDING (available_at moved)
 *    │                    ├── consumer throws, no attempts left ──&gt; FAILED
 *    └────────────────────┘ lock expired (crash) — reclaimed by the poller
 * </pre>
 */
public enum DeliveryStatus {

    /** Waiting to be claimed, from {@code available_at} onwards. */
    PENDING,

    /** Claimed by a poller and handed to a worker. */
    PROCESSING,

    /** The consumer returned normally and the delivery is complete. */
    PROCESSED,

    /** Exhausted its attempts, or failed in a way retrying cannot fix. Never retried again. */
    FAILED
}
