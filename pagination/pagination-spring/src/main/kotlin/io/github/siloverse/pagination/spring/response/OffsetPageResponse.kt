package io.github.siloverse.pagination.spring.response

data class OffsetPageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)