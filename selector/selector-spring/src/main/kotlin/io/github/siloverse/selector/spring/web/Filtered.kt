package io.github.siloverse.selector.spring.web

/**
 * Marks a controller parameter of type [io.github.siloverse.selector.core.filter.Selection]
 * and names the [io.github.siloverse.selector.core.definition.FilterDefinition] bean to parse
 * the query string against. One application can serve many lists, each with its own definition.
 *
 * ```
 * @GetMapping("/listings")
 * fun listings(@Filtered("listingFilters") selection: Selection, request: KeysetRequest): ...
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Filtered(val definition: String)
