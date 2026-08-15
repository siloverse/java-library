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
 *
 * <p>Scope of "committed effects": the transaction is thread-bound and joined by
 * propagation. Work joins it only when it runs synchronously on the invoking thread against
 * the same {@code DataSource}/transaction manager -- including {@code @Transactional
 * (REQUIRED)} handlers and transaction-aware bus publishes. Work on another thread, another
 * {@code DataSource}, or under {@code REQUIRES_NEW} commits separately and falls outside
 * the guarantee. See {@link Consumer#dedup()} for the full rules.
 */
public interface Inbox {

    boolean processOnce(String consumerId, UUID messageId, Runnable invocation);
}
