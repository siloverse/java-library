package io.github.siloverse.messaging.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method of a Spring bean as a message consumer.
 *
 * <p>A consumer method must take exactly one argument implementing
 * {@link io.github.siloverse.messaging.api.Command} or
 * {@link io.github.siloverse.messaging.api.Event}, and must return {@code void}. Invalid
 * definitions fail application startup.
 *
 * <pre>{@code
 * @Component
 * class OrderConsumers {
 *
 *     @Consumer
 *     void handle(ConfirmOrder command) { ... }
 *
 *     @Consumer
 *     void on(OrderConfirmed event) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Consumer {

    /**
     * Relative order among the consumers of the same event type. Lower values run first during
     * synchronous dispatch. Ignored for commands, which have a single consumer, and irrelevant for
     * durable asynchronous dispatch, where deliveries are independent.
     *
     * @return the order, defaults to {@code 0}
     */
    int order() default 0;
}
