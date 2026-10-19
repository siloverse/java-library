package io.github.siloverse.pagination.core.offset

/**
 * One page of results without a total count.
 *
 * A slice answers only one question about the rest of the list: is there a next page?
 * It never runs a COUNT query, so it is cheaper than [OffsetPage] on large tables.
 * The price is that the caller cannot show "page 2 of 7".
 *
 * The request is the same [OffsetRequest]. What differs is what you pay for.
 */
data class Slice<T>(
    val items: List<T>,
    val request: OffsetRequest,
    val hasNext: Boolean,
) {

    init {
        require(items.size <= request.size) {
            "slice holds ${items.size} items but the request size is ${request.size}"
        }
    }

    val hasPrevious: Boolean
        get() = request.page > 0

    companion object {

        /**
         * Builds a slice from a query that fetched one extra row.
         *
         * This is the standard trick: ask the database for `size + 1` rows. If it returns
         * `size + 1`, a next page exists and the extra row is dropped. If it returns `size`
         * or fewer, this is the last page.
         */
        fun <T> fromOverFetch(rows: List<T>, request: OffsetRequest): Slice<T> {
            require(rows.size <= request.size + 1) {
                "fetched ${rows.size} rows but the overfetch limit is ${request.size + 1}"
            }
            return Slice(
                items = rows.take(request.size),
                request = request,
                hasNext = rows.size > request.size,
            )
        }
    }
}
