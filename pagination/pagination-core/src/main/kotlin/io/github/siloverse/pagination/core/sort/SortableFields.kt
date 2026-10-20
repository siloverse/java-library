package io.github.siloverse.pagination.core.sort

import io.github.siloverse.pagination.core.keyset.KeysetKey
import io.github.siloverse.pagination.core.keyset.KeysetKeys

/**
 * The order contract of one list endpoint: which fields a client may sort by, each as a
 * [KeysetKey] so a cursor can be built from it, the tie breaker every sort ends with, and
 * the order used when the client sends none.
 *
 * Written once, next to the list. The [tieBreaker] must be one of the [keys] and unique on
 * its own (usually `id`). It is appended to every sort so that paging, keyset above all,
 * never skips rows that tie. Offset and slice lists declare the same thing; they just never
 * ask for a cursor.
 */
class SortableFields(
    val keys: List<KeysetKey<*>>,
    val tieBreaker: String,
    val defaultSort: List<SortOrder> = emptyList(),
) {

    constructor(vararg keys: KeysetKey<*>, tieBreaker: String, defaultSort: List<SortOrder> = emptyList()) :
        this(keys.toList(), tieBreaker, defaultSort)

    private val byName: Map<String, KeysetKey<*>> = keys.associateBy { it.property }

    val fields: List<String>
        get() = keys.map { it.property }

    init {
        require(keys.isNotEmpty()) { "a list needs at least one sortable field" }
        require(fields.all { it.isNotBlank() && it.none { c -> c == ',' || c.isWhitespace() } }) { "sortable field names may not be blank or contain ',' or spaces: $fields" }
        require(byName.size == keys.size) { "a sortable field appears twice: $fields" }
        require(tieBreaker in byName) { "tie breaker '$tieBreaker' is not a sortable field" }
        defaultSort.forEach { require(it.field in byName) { "default sort names a field that is not sortable: '${it.field}'" } }
    }

    fun isSortable(name: String): Boolean = name in byName

    /** The order used when the client sends no `sort`: the default sort, with the tie breaker last. */
    val defaultOrders: List<SortOrder>
        get() = withTieBreaker(defaultSort)

    /** The keys for a validated sort, each with the direction the client chose. Total by construction. */
    fun keysFor(sort: SortRequest): KeysetKeys =
        KeysetKeys(sort.orders.map { order -> byName.getValue(order.field).withDirection(order.direction) })

    /** Appends the tie breaker unless it is already the last order. */
    internal fun withTieBreaker(orders: List<SortOrder>): List<SortOrder> =
        if (orders.lastOrNull()?.field == tieBreaker) orders else orders + SortOrder(tieBreaker, Direction.ASC)
}
