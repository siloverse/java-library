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
     */
    boolean dedup() default false;
}
