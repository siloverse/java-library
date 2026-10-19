package io.github.siloverse.pagination.spring.response

data class SliceResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)