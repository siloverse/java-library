package io.github.siloverse.pagination.core.sort

import io.github.siloverse.pagination.core.error.PaginationException

/**
 * The validated ORDER BY of one request. Always ends with the list's tie breaker, so it is
 * total: every row has one fixed place, which keyset paging depends on.
 *
 * Wire format is Spring's: `sort=price,desc&sort=createdAt`. Direction is optional and
 * case-insensitive; field names are case-sensitive. Any mistake is a [PaginationException]
 * naming `sort`. Lives in pagination because the order of a list is what keyset paging is
 * built on; the selector library only builds the WHERE clause.
 */
data class SortRequest(val orders: List<SortOrder>) {

    init {
        require(orders.isNotEmpty()) { "a sort needs at least one order" }
        require(orders.map { it.field }.distinct().size == orders.size) { "a sort field appears twice" }
    }

    /** `price:desc,id:asc` — names this order, so a cursor can say which sort it was built for. */
    val signature: String
        get() = orders.joinToString(",") { "${it.field}:${it.direction.name.lowercase()}" }

    companion object {
        const val PARAMETER = "sort"

        /** [raw] is every `sort` value the client sent, in order. Null or empty means the list's default. */
        fun parse(sortable: SortableFields, raw: List<String>?): SortRequest {
            if (raw.isNullOrEmpty()) return SortRequest(sortable.defaultOrders)

            val orders = raw.map { parseOne(sortable, it) }

            val duplicate = orders.groupBy { it.field }.entries.firstOrNull { it.value.size > 1 }?.key
            if (duplicate != null) throw PaginationException(PARAMETER, "field '$duplicate' appears twice")

            val tieIndex = orders.indexOfFirst { it.field == sortable.tieBreaker }
            if (tieIndex in 0 until orders.lastIndex) {
                throw PaginationException(PARAMETER, "'${sortable.tieBreaker}' must be the last sort field")
            }

            return SortRequest(sortable.withTieBreaker(orders))
        }

        private fun parseOne(sortable: SortableFields, text: String): SortOrder {
            val parts = text.split(",")
            if (parts.size > 2 || parts[0].isBlank()) throw PaginationException(PARAMETER, "'$text' is not 'field' or 'field,direction'")
            val name = parts[0].trim()
            if (!sortable.isSortable(name)) throw PaginationException(PARAMETER, "'$name' is not sortable; sortable fields: ${sortable.fields}")
            val direction = if (parts.size == 1) {
                Direction.ASC
            } else {
                Direction.fromText(parts[1].trim()) ?: throw PaginationException(PARAMETER, "'${parts[1]}' is not 'asc' or 'desc'")
            }
            return SortOrder(name, direction)
        }
    }
}
