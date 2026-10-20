package io.github.siloverse.selector.core.filter

import io.github.siloverse.selector.core.definition.Field
import io.github.siloverse.selector.core.definition.Operator

/**
 * One parsed, typed comparison: `price.lt=50` becomes `Condition(price, LT, [50L])`.
 *
 * [values] holds exactly one value for every operator except [Operator.IN], which holds
 * one or more. The engine adapter reads the field, the operator and the typed values and
 * writes the predicate; it never sees the client's text.
 */
class Condition<T : Any>(
    val field: Field<T>,
    val operator: Operator,
    val values: List<T>,
) {

    init {
        require(values.isNotEmpty()) { "a condition needs at least one value" }
        require(operator == Operator.IN || values.size == 1) { "operator ${operator.suffix} takes exactly one value, got ${values.size}" }
    }

    /** The single value, for every operator but IN. */
    val value: T
        get() = values.single()

    override fun toString(): String = "${field.name}.${operator.suffix}=${values.joinToString(",")}"
}
