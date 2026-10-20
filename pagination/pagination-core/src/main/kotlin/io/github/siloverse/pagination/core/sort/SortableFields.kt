package io.github.siloverse.pagination.core.sort

/**
 * The order contract of one list endpoint: which fields a client may sort by, the tie
 * breaker every sort ends with, and the order used when the client sends none.
 *
 * Written once, next to the list. The [tieBreaker] must be one of the [fields] and unique
 * on its own (usually `id`). It is appended to every sort so that paging, keyset above all,
 * never skips rows that tie.
 */
class SortableFields(
    val fields: List<String>,
    val tieBreaker: String,
    val defaultSort: List<SortOrder> = emptyList(),
) {

    constructor(vararg fields: String, tieBreaker: String, defaultSort: List<SortOrder> = emptyList()) :
        this(fields.toList(), tieBreaker, defaultSort)

    init {
        require(fields.isNotEmpty()) { "a list needs at least one sortable field" }
        require(fields.all { it.isNotBlank() && it.none { c -> c == ',' || c.isWhitespace() } }) { "sortable field names may not be blank or contain ',' or spaces: $fields" }
        require(fields.distinct().size == fields.size) { "a sortable field appears twice: $fields" }
        require(tieBreaker in fields) { "tie breaker '$tieBreaker' is not a sortable field" }
        defaultSort.forEach { require(it.field in fields) { "default sort names a field that is not sortable: '${it.field}'" } }
    }

    fun isSortable(name: String): Boolean = name in fields

    /** The order used when the client sends no `sort`: the default sort, with the tie breaker last. */
    val defaultOrders: List<SortOrder>
        get() = withTieBreaker(defaultSort)

    /** Appends the tie breaker unless it is already the last order. */
    internal fun withTieBreaker(orders: List<SortOrder>): List<SortOrder> =
        if (orders.lastOrNull()?.field == tieBreaker) orders else orders + SortOrder(tieBreaker, Direction.ASC)
}
