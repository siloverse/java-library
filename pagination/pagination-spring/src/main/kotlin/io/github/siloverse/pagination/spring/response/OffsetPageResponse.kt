package io.github.siloverse.pagination.spring.response

/**
 * The JSON contract for an offset page. Flat on purpose: a client never sees a nested
 * `request` object. Spring warns against returning its own `Page<T>` because its JSON is
 * not a stable contract; this class is that contract.
 */
data class OffsetPageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)