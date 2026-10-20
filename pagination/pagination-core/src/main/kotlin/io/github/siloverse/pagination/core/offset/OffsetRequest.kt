package io.github.siloverse.pagination.core.offset

import io.github.siloverse.pagination.core.sort.SortRequest

/**
 * Asks for one page of a list by position: "give me page [page], [size] items per page".
 *
 * Pages are counted from zero. A page past the end of the list is a legal request;
 * it just comes back empty. A negative page or a size below one is never legal,
 * so the constructor rejects it.
 *
 * [sort] is the client's validated order, or null when the list has a fixed order chosen by the
 * developer. Offset paging works either way; it only needs a sort to be stable across pages.
 */
data class OffsetRequest(val page: Int, val size: Int, val sort: SortRequest? = null) {

    init {
        require(page >= 0) { "page must be 0 or more, was $page" }
        require(size >= 1) { "size must be 1 or more, was $size" }
    }

    /** How many rows to skip before this page starts. Long, because page * size can pass Int. */
    val offset: Long
        get() = page.toLong() * size
}
