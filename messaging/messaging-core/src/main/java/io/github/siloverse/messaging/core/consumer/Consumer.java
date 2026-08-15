package io.github.siloverse.messaging.core.consumer;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Consumer {

    String id();

    /**
     * Opt-in inbox deduplication for handlers that cannot be idempotent.
     *
     * <p>Default {@code false}: by writing a consumer you declare it idempotent -- delivery
     * is at-least-once and duplicates WILL arrive (ask: "would this method running twice with
     * the same message change the outcome twice?" -- setting absolute state is safe;
     * increments, appends and insert-per-event are not).
     *
     * <p>{@code true}: the listener runs the invocation through an {@link Inbox} -- the
     * ledger entry (consumer id + message id) and the business effects commit or roll back
     * in ONE transaction, so effects commit exactly once. Invocations may still repeat, and
     * side effects OUTSIDE that transaction (emails, HTTP calls) remain the handler's
     * responsibility; publish follow-up messages via the transaction-aware bus to keep them
     * inside the guarantee.
     *
     * <p><b>Transaction scope rules</b> (the transaction is thread-bound and joined by
     * propagation). INSIDE the guarantee: synchronous work on the consumer thread against
     * the service's own {@code DataSource} (repositories, {@code JdbcTemplate});
     * {@code @Transactional} on the handler with default {@code REQUIRED} propagation
     * (joins, one commit); {@code TransactionAwareAsynchronousBus} publishes (the outbox row
     * commits atomically with the inbox row -- exactly-once message chaining). OUTSIDE, no
     * exceptions: a second {@code DataSource} or transaction manager (that would be 2PC);
     * anything on ANOTHER THREAD (executors, {@code CompletableFuture} -- it looks inside
     * the method but leaves the transaction); {@code REQUIRES_NEW}; emails, HTTP calls,
     * files, direct {@code AsynchronousBus} publishes.
     */
    boolean dedup() default false;
}
