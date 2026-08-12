package io.github.siloverse.messaging.core.api;

/**
 * In-process, in-transaction message bus. Consumers are <em>participants</em> in the caller's operation: same thread,
 * same transaction, same fate.
 *
 * <p>Publishing here is a structured method call. The publisher does not know who
 * runs, but everything that runs is part of the publisher's own unit of work:
 *
 * <ul>
 *   <li><strong>Same thread, sequential.</strong> When {@code publish} returns,
 *       every consumer has fully executed, in registration order. There is no
 *       concurrency: a database transaction lives on one connection and one
 *       thread, so concurrent consumers would necessarily leave the transaction.
 *       A consumer too slow to run sequentially inside a transaction is not a
 *       participant -- move it to an asynchronous bus.</li>
 *   <li><strong>Same transaction.</strong> Consumer writes join the caller's
 *       active transaction and commit or roll back with it. Consumers see the
 *       caller's uncommitted state; there is no read-your-own-write race.</li>
 *   <li><strong>All or nothing.</strong> The first consumer failure stops the
 *       remaining consumers, propagates to the publisher as the original
 *       exception, and -- via the caller's transaction boundary -- rolls back
 *       everything: the publisher's writes and all completed consumers' writes.
 *       The operation atomically never happened. (The rollback is performed by
 *       the transaction, not by this bus; the bus's contribution is refusing to
 *       hide the failure.)</li>
 *   <li><strong>Nothing survives a crash.</strong> No persistence, no retry, no
 *       redelivery. This is safe precisely because of atomicity: a crash
 *       mid-dispatch means the transaction never committed, so no half-applied
 *       state exists to recover.</li>
 * </ul>
 *
 * <p><strong>Choosing a bus:</strong> use this bus when consumers are part of the
 * operation -- if they fail, the operation must not stand (billing must invoice,
 * or the confirmation is wrong). Use an asynchronous bus when consumers are
 * reactions to the operation -- the operation is complete without them (send the
 * email, warm the cache). Participant &rarr; synchronous; observer &rarr;
 * asynchronous.
 *
 * <p><strong>Migration warning:</strong> these semantics quietly invite consumers
 * to rely on rollback-on-failure. Over a broker, a consumer runs in its own
 * transaction and its failure can no longer abort the publisher -- with no
 * compile error. Before moving an event to an asynchronous bus, audit every
 * consumer and ask: if this ran twice, or if its failure did NOT abort the
 * publisher, is the system still correct? If not, the consumer needs redesign
 * before the event may leave the JVM.
 *
 * <p>This is the same contract as Spring's default synchronous
 * {@code @EventListener} dispatch, with command semantics added.
 */
public interface SynchronousBus {

    /**
     * Delivers the event to every registered consumer, sequentially, in registration order, in the calling thread.
     *
     * <p>Zero consumers is a legitimate state, not an error: this returns
     * silently. The publisher must not depend on anyone listening.
     *
     * <p>Any consumer failure propagates from this method as the consumer's
     * original exception (checked exceptions wrapped in
     * {@link io.github.siloverse.messaging.core.error.ConsumerInvocationException}). Consumers registered after the
     * failing one are not invoked.
     */
    void publish(Event event);

    /**
     * Delivers the command to its single registered consumer, in the calling thread, fire-and-forget: no result is
     * returned even though the consumer runs synchronously, keeping this signature portable across all buses.
     *
     * <p>Consumer failures propagate to the caller.
     *
     * @throws io.github.siloverse.messaging.core.error.NoHandlerException if no consumer is registered -- a
     *         command with nobody to execute it is a wiring error, unlike an event with no subscribers
     */
    void send(Command command);
}

