package io.github.siloverse.pagination.spring.util

import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.offset.OffsetPage
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.offset.Slice
import io.github.siloverse.pagination.spring.response.KeysetPageResponse
import io.github.siloverse.pagination.spring.response.OffsetPageResponse
import io.github.siloverse.pagination.spring.response.SliceResponse

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/*
 * Two groups of one-line bridges.
 *
 * Out: core page types to the flat response models in `spring.response`, which are the
 * JSON contract. A controller calls `toResponse()` last and returns the result.
 *
 * In: `OffsetRequest` to Spring Data's `Pageable`, and Spring Data's `Page` / `Slice` back
 * to the core types. Spring Data already does LIMIT, OFFSET and COUNT well; what it does
 * not give you is the policy on the way in and a stable response shape on the way out.
 */

fun <T> OffsetPage<T>.toResponse(): OffsetPageResponse<T> =
    OffsetPageResponse(items, request.page, request.size, totalElements, totalPages, hasPrevious, hasNext)

fun <T> Slice<T>.toResponse(): SliceResponse<T> =
    SliceResponse(items, request.page, request.size, hasPrevious, hasNext)

fun <T> KeysetPage<T>.toResponse(): KeysetPageResponse<T> =
    KeysetPageResponse(items, request.size, nextCursor?.value, hasNext)

/** Hands a validated request to a Spring Data repository. Sort is the caller's choice. */
fun OffsetRequest.toPageable(sort: Sort = Sort.unsorted()): Pageable =
    PageRequest.of(page, size, sort)

/** Wraps a counted Spring Data page. */
fun <T : Any> Page<T>.toOffsetPage(request: OffsetRequest): OffsetPage<T> =
    OffsetPage(content, request, totalElements)

/** Wraps an uncounted Spring Data slice. */
fun <T : Any> org.springframework.data.domain.Slice<T>.toSlice(request: OffsetRequest): Slice<T> =
    Slice(content, request, hasNext())