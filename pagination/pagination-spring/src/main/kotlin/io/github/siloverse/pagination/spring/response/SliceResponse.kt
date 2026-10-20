package io.github.siloverse.pagination.spring.response

/**
 * The JSON contract for a slice: an offset page without the totals, because no COUNT ran.
 */
data class SliceResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)