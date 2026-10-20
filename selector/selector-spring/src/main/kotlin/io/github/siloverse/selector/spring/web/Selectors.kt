package io.github.siloverse.selector.spring.web

import kotlin.reflect.KClass

/**
 * Marks a controller parameter of type `List<Selector>` and names the entity whose filters the
 * query string is parsed against. Also read by the pagination resolver, so the entity is named once.
 *
 * ```
 * @GetMapping("/orders")
 * fun orders(@Selectors(entity = Order::class) selectors: List<Selector>, request: KeysetRequest): ...
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Selectors(val entity: KClass<*>)
