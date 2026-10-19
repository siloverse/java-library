package io.github.siloverse.pagination.spring

import io.github.siloverse.pagination.core.KeysetPage
import io.github.siloverse.pagination.core.OffsetPage
import io.github.siloverse.pagination.core.Slice

/**
 * The JSON contract. Flat on purpose: a client should never see a nested `request` object
 * or a cursor wrapped in `{ "value": ... }`. These are the only types a controller returns.
 *
 * Spring warns against returning its own `Page<T>` because its JSON is not a stable contract.
 * These classes are that contract.
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

data class SliceResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

data class KeysetPageResponse<T>(
    val items: List<T>,
    val size: Int,
    /** Pass this back as `after` to get the next page. Null on the last page. */
    val nextCursor: String?,
    val hasNext: Boolean,
)

fun <T> OffsetPage<T>.toResponse(): OffsetPageResponse<T> =
    OffsetPageResponse(items, request.page, request.size, totalElements, totalPages, hasPrevious, hasNext)

fun <T> Slice<T>.toResponse(): SliceResponse<T> =
    SliceResponse(items, request.page, request.size, hasPrevious, hasNext)

fun <T> KeysetPage<T>.toResponse(): KeysetPageResponse<T> =
    KeysetPageResponse(items, request.size, nextCursor?.value, hasNext)
