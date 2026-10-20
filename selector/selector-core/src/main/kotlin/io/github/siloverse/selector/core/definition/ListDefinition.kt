package io.github.siloverse.selector.core.definition

import io.github.siloverse.selector.core.sort.Direction
import io.github.siloverse.selector.core.sort.SortOrder

/**
 * The contract of one list endpoint: the fields a client may filter and sort by, and the
 * tie breaker that every sort ends with.
 *
 * Written once, next to the list. Everything downstream reads from it: the filter parser,
 * the sort parser, the keyset keys. It is also the list's documentation.
 *
 * The [tieBreaker] must be a sortable field that is unique on its own (usually `id`). It is
 * appended to every sort so that paging, keyset above all, never skips rows that tie.
 */
class ListDefinition(
    val fields: List<Field<*>>,
    val tieBreaker: String,
    val defaultSort: List<SortOrder> = emptyList(),
) {

    constructor(vararg fields: Field<*>, tieBreaker: String, defaultSort: List<SortOrder> = emptyList()) :
        this(fields.toList(), tieBreaker, defaultSort)

    private val byName: Map<String, Field<*>> = fields.associateBy { it.name }

    init {
        require(fields.isNotEmpty()) { "a list needs at least one field" }
        require(byName.size == fields.size) { "a field name appears twice: ${fields.map { it.name }}" }
        val tie = byName[tieBreaker] ?: throw IllegalArgumentException("tie breaker '$tieBreaker' is not a declared field")
        require(tie.sortable) { "tie breaker '$tieBreaker' must be sortable" }
        defaultSort.forEach { order ->
            val field = byName[order.field] ?: throw IllegalArgumentException("default sort names unknown field '${order.field}'")
            require(field.sortable) { "default sort field '${order.field}' is not sortable" }
        }
    }

    fun field(name: String): Field<*>? = byName[name]

    val sortableFields: List<String>
        get() = fields.filter { it.sortable }.map { it.name }

    /** The order used when the client sends no `sort`: the default sort, with the tie breaker last. */
    val defaultOrders: List<SortOrder>
        get() = withTieBreaker(defaultSort)

    /** Appends the tie breaker unless it is already the last order. */
    internal fun withTieBreaker(orders: List<SortOrder>): List<SortOrder> =
        if (orders.lastOrNull()?.field == tieBreaker) orders else orders + SortOrder(tieBreaker, Direction.ASC)
}
