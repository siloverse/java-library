package io.github.siloverse.selector.core.sort

import io.github.siloverse.selector.core.definition.ListDefinition
import io.github.siloverse.selector.core.error.SelectorException

/**
 * The validated ORDER BY of one request. Always ends with the list's tie breaker, so it is
 * total: every row has one fixed place, which keyset paging depends on.
 *
 * Wire format is Spring's: `sort=price,desc&sort=createdAt`. Direction is optional and
 * case-insensitive; field names are case-sensitive. Any mistake is a [SelectorException]
 * naming `sort`.
 */
data class SortRequest(val orders: List<SortOrder>) {

    init {
        require(orders.isNotEmpty()) { "a sort needs at least one order" }
        require(orders.map { it.field }.distinct().size == orders.size) { "a sort field appears twice" }
    }

    companion object {
        const val PARAMETER = "sort"

        /** [raw] is every `sort` value the client sent, in order. Null or empty means the list's default. */
        fun parse(definition: ListDefinition, raw: List<String>?): SortRequest {
            if (raw.isNullOrEmpty()) return SortRequest(definition.defaultOrders)

            val orders = raw.map { parseOne(definition, it) }

            val duplicate = orders.groupBy { it.field }.entries.firstOrNull { it.value.size > 1 }?.key
            if (duplicate != null) throw SelectorException(PARAMETER, "field '$duplicate' appears twice")

            val tieIndex = orders.indexOfFirst { it.field == definition.tieBreaker }
            if (tieIndex in 0 until orders.lastIndex) {
                throw SelectorException(PARAMETER, "'${definition.tieBreaker}' must be the last sort field")
            }

            return SortRequest(definition.withTieBreaker(orders))
        }

        private fun parseOne(definition: ListDefinition, text: String): SortOrder {
            val parts = text.split(",")
            if (parts.size > 2 || parts[0].isBlank()) throw SelectorException(PARAMETER, "'$text' is not 'field' or 'field,direction'")
            val name = parts[0].trim()
            val field = definition.field(name) ?: throw SelectorException(PARAMETER, "unknown field '$name'")
            if (!field.sortable) throw SelectorException(PARAMETER, "'$name' is not sortable; sortable fields: ${definition.sortableFields}")
            val direction = if (parts.size == 1) {
                Direction.ASC
            } else {
                Direction.fromText(parts[1].trim()) ?: throw SelectorException(PARAMETER, "'${parts[1]}' is not 'asc' or 'desc'")
            }
            return SortOrder(name, direction)
        }
    }
}
