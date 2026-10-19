package io.github.siloverse.pagination.spring

import io.github.siloverse.pagination.core.OffsetPage
import io.github.siloverse.pagination.core.OffsetRequest
import io.github.siloverse.pagination.core.Slice
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * Bridges between the library's offset types and Spring Data's. Each is one line on purpose:
 * Spring Data already does LIMIT, OFFSET and COUNT well. What it does not give you is the
 * policy on the way in and a stable response shape on the way out.
 */

/** Hands a validated request to a Spring Data repository. Sort is the caller's choice. */
fun OffsetRequest.toPageable(sort: Sort = Sort.unsorted()): Pageable =
    PageRequest.of(page, size, sort)

/** Wraps a counted Spring Data page. */
fun <T : Any> Page<T>.toOffsetPage(request: OffsetRequest): OffsetPage<T> =
    OffsetPage(content, request, totalElements)

/** Wraps an uncounted Spring Data slice. */
fun <T : Any> org.springframework.data.domain.Slice<T>.toSlice(request: OffsetRequest): Slice<T> =
    Slice(content, request, hasNext())
