package io.github.siloverse.selector.core.spec

import io.github.siloverse.selector.core.error.SelectorException
import io.github.siloverse.selector.core.selector.Selector
import io.github.siloverse.selector.core.selector.Selectors
import kotlin.reflect.KClass

/**
 * One filter a list offers: its key, the entity it belongs to, the type of its values and how
 * many values it takes. Engine adapters add how the filter becomes a predicate.
 *
 * Declared once per filter as a bean, found by scanning, registered by entity and key.
 * [parse] is the only place client text becomes typed values, and the only place it is
 * refused: wrong count, empty value, or text the type cannot read. Every refusal names the
 * query key.
 */
abstract class SelectorSpec<T : Any>(
    val entityType: KClass<*>,
    val namespace: String,
    val attribute: String,
    val constraint: String? = null,
    val type: FieldType<T>,
    val arity: Arity,
) {

    enum class Arity { SINGLE, MULTI }

    val key: String
        get() = Selectors.key(namespace, attribute, constraint)

    fun parse(selector: Selector): List<T> {
        val values = selector.values
        if (values.isEmpty()) throw SelectorException(key, "has no value")
        if (arity == Arity.SINGLE && values.size != 1) throw SelectorException(key, "takes exactly one value, got ${values.size}")
        if (values.any { it.isBlank() }) throw SelectorException(key, "has an empty value")
        return values.map { text ->
            try {
                type.parse(text.trim())
            } catch (e: IllegalArgumentException) {
                throw SelectorException(key, e.message ?: "has an invalid value")
            }
        }
    }

    override fun toString(): String = "${entityType.simpleName} $key (${type.name}, ${arity.name.lowercase()})"
}
