package io.github.siloverse.pagination.core.offset

/**
 * One page of results for an [OffsetRequest], plus the total count so the caller
 * can tell how many pages exist.
 */
data class OffsetPage<T>(
    val items: List<T>,
    val request: OffsetRequest,
    val totalElements: Long,
) {

    init {
        require(totalElements >= 0) { "totalElements must be 0 or more, was $totalElements" }
        require(items.size <= request.size) {
            "page holds ${items.size} items but the request size is ${request.size}"
        }
    }

    /** Ceiling division: 23 items at size 10 is 3 pages, 0 items is 0 pages. */
    val totalPages: Int
        get() = ((totalElements + request.size - 1) / request.size).toInt()

    val hasPrevious: Boolean
        get() = request.page > 0

    val hasNext: Boolean
        get() = request.page + 1 < totalPages

    /** Same page, items converted. For turning entities into DTOs before the response. */
    fun <R> map(transform: (T) -> R): OffsetPage<R> = OffsetPage(items.map(transform), request, totalElements)
}
