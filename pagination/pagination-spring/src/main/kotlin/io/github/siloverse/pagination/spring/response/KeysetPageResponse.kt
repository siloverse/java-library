package io.github.siloverse.pagination.spring.response

data class KeysetPageResponse<T>(
    val items: List<T>,
    val size: Int,
    /** Pass this back as `after` to get the next page. Null on the last page. */
    val nextCursor: String?,
    val hasNext: Boolean,
)