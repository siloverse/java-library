package io.github.siloverse.messaging.core.consumer;

import java.util.UUID;

/**
 * Deduplication ledger for consumers declared {@code @Consumer(dedup = true)}.
 *
 * <p>Contract: {@code processOnce} runs the invocation and the ledger write ATOMICALLY --
 * in the JDBC implementation, one database transaction holds the {@code (consumerId,
 * messageId)} insert and the business effects, so they commit or roll back together. A
 * duplicate (the pair already recorded) returns {@code false} WITHOUT running the
 * invocation; the caller acknowledges the delivery either way. An invocation that throws
 * propagates after the ledger entry is rolled back, so a redelivery passes the gate and
 * runs again.
 *
 * <p>The resulting guarantee: at-least-once invocation, exactly-once COMMITTED effects.
 * Side effects outside the transaction are not covered.
 */
public interface Inbox {

    boolean processOnce(String consumerId, UUID messageId, Runnable invocation);
}
