package io.github.siloverse.selector.core.filter

import io.github.siloverse.selector.core.definition.Field
import io.github.siloverse.selector.core.definition.FilterDefinition
import io.github.siloverse.selector.core.definition.Operator
import io.github.siloverse.selector.core.error.SelectorException

/**
 * The validated WHERE clause of one request: every condition the client asked for, typed,
 * to be ANDed. Empty means no filter.
 *
 * Wire format: `field=value` is equals; `field.op=value` is any other operator, with the
 * suffixes of [Operator]; `field.in=a,b,c` takes a comma-separated list. A query key the
 * list does not declare is a client error, not something to ignore: a typo in a filter must
 * not silently return the whole table. Keys that belong to paging and sorting are skipped
 * through [reserved].
 */
data class Selection(val conditions: List<Condition<*>>) {

    val isEmpty: Boolean
        get() = conditions.isEmpty()

    companion object {
        val NONE = Selection(emptyList())

        /** Query keys that are not filters: page, size, after, sort. Pass your own set to change it. */
        val DEFAULT_RESERVED = setOf("page", "size", "after", "sort")

        private const val LIST_SEPARATOR = ","

        /** [params] is the whole query string, each key with every value the client sent for it. */
        fun parse(
            definition: FilterDefinition,
            params: Map<String, List<String>>,
            reserved: Set<String> = DEFAULT_RESERVED,
        ): Selection {
            val conditions = params.entries
                .filter { it.key !in reserved }
                .map { (key, values) -> parseOne(definition, key, values) }
            return Selection(conditions)
        }

        private fun parseOne(definition: FilterDefinition, key: String, rawValues: List<String>): Condition<*> {
            val parts = key.split(".")
            if (parts.size > 2 || parts[0].isBlank()) throw SelectorException(key, "is not 'field' or 'field.operator'")

            val field = definition.field(parts[0])
                ?: throw SelectorException(key, "unknown filter field '${parts[0]}'; fields: ${definition.fieldNames}")
            val operator = if (parts.size == 1) {
                Operator.EQ
            } else {
                Operator.fromSuffix(parts[1]) ?: throw SelectorException(key, "unknown operator '${parts[1]}'; operators: ${Operator.entries.map { it.suffix }}")
            }
            if (operator !in field.operators) {
                throw SelectorException(key, "operator '${operator.suffix}' is not allowed on '${field.name}'; allowed: ${field.operators.map { it.suffix }}")
            }

            if (rawValues.size != 1) throw SelectorException(key, "given ${rawValues.size} times, expected once")
            val raw = rawValues.single()
            val texts = if (operator == Operator.IN) raw.split(LIST_SEPARATOR) else listOf(raw)
            if (texts.any { it.isBlank() }) throw SelectorException(key, "has an empty value")

            return condition(field, operator, texts, key)
        }

        private fun <T : Any> condition(field: Field<T>, operator: Operator, texts: List<String>, key: String): Condition<T> {
            val values = texts.map { text ->
                try {
                    field.type.parse(text.trim())
                } catch (e: IllegalArgumentException) {
                    throw SelectorException(key, e.message ?: "has an invalid value")
                }
            }
            return Condition(field, operator, values)
        }
    }
}
