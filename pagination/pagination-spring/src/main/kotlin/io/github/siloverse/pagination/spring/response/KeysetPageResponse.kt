package io.github.siloverse.pagination.spring.response

/**
 * The JSON contract for a keyset page. The cursor is a plain string, never wrapped in
 * `{ "value": ... }`. Pass it back as `after` to get the next page.
 */
data class KeysetPageResponse<T>(
    val items: List<T>,
    val size: Int,
    /** Pass this back as `after` to get the next page. Null on the last page. */
    val nextCursor: String?,
    val hasNext: Boolean,
)