package io.github.siloverse.selector.core.selector

import kotlin.reflect.KClass

/**
 * One filter as the client sent it: `filter.order.checked-out-at.after=2026-01-01T00:00:00Z`
 * becomes namespace `order`, attribute `checked-out-at`, constraint `after`, one raw value.
 *
 * Values are still text. The matching [io.github.siloverse.selector.core.spec.SelectorSpec]
 * parses them, once, and says no when they do not fit.
 */
data class Selector(
    val entityType: KClass<*>,
    val namespace: String,
    val attribute: String,
    val constraint: String?,
    val values: List<String>,
) {

    /** The query key this selector came from. Every error names it. */
    val key: String
        get() = Selectors.key(namespace, attribute, constraint)
}
