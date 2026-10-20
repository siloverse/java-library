package io.github.siloverse.pagination.core.keyset

import io.github.siloverse.pagination.core.sort.SortRequest

import io.github.siloverse.pagination.core.offset.OffsetRequest

/**
 * Asks for the next [size] rows after a bookmark: "give me what comes after [after]".
 *
 * A null [after] means start from the beginning. Unlike [OffsetRequest] there is no
 * page number. You cannot jump to page 7. You can only walk forward from where you are.
 * In return, every page costs the same no matter how deep you are, and an inserted row
 * never makes the next page repeat a row you already saw.
 *
 * The [sort] is part of the request because the cursor is made of the sort values: there is no
 * keyset page without a total order. It arrives validated against the list's sort contract.
 */
data class KeysetRequest(val after: Cursor?, val size: Int, val sort: SortRequest) {

    init {
        require(size >= 1) { "size must be 1 or more, was $size" }
    }

    val isFirstPage: Boolean
        get() = after == null
}
