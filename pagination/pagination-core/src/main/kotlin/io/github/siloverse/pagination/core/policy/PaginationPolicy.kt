package io.github.siloverse.pagination.core.policy

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.keyset.Cursor
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetRequest

/**
 * The one door from raw client input to a valid request.
 *
 * Fills in [defaultSize] when the client gave no size. Rejects a size above [maxSize].
 * It rejects, it never clamps: a client that asks for 10000 rows is told no, not quietly
 * handed [maxSize]. Every rejection is a [PaginationException] naming the bad parameter.
 *
 * Input arrives as nullable values. Null means "the client did not send it". Turning the
 * raw query string into an Int is the web adapter's job; deciding whether that Int is
 * acceptable is this class's job.
 */
data class PaginationPolicy(val defaultSize: Int, val maxSize: Int) {

    init {
        require(maxSize >= 1) { "maxSize must be 1 or more, was $maxSize" }
        require(defaultSize in 1..maxSize) { "defaultSize must be between 1 and maxSize ($maxSize), was $defaultSize" }
    }

    fun offset(page: Int?, size: Int?): OffsetRequest {
        val resolvedPage = page ?: 0
        if (resolvedPage < 0) throw PaginationException("page", "must be 0 or more, was $resolvedPage")
        return OffsetRequest(resolvedPage, resolveSize(size))
    }

    fun keyset(after: String?, size: Int?): KeysetRequest =
        KeysetRequest(after?.let(::parseCursor), resolveSize(size))

    private fun resolveSize(size: Int?): Int {
        val resolved = size ?: defaultSize
        if (resolved < 1) throw PaginationException("size", "must be 1 or more, was $resolved")
        if (resolved > maxSize) throw PaginationException("size", "must be at most $maxSize, was $resolved")
        return resolved
    }

    /** A cursor the client sends back must be one we gave out: non-blank and decodable. */
    private fun parseCursor(raw: String): Cursor {
        val cursor = try {
            Cursor(raw).also { it.parts() }
        } catch (e: IllegalArgumentException) {
            throw PaginationException("after", "is not a valid cursor")
        }
        return cursor
    }

    companion object {
        val DEFAULT = PaginationPolicy(defaultSize = 20, maxSize = 100)
    }
}
