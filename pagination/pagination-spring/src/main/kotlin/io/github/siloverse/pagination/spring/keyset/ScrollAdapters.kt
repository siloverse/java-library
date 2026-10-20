package io.github.siloverse.pagination.spring.keyset

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.keyset.Cursor
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import org.springframework.data.domain.KeysetScrollPosition
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Window

/*
 * Bridges between the library's keyset types and Spring Data's scrolling.
 *
 * In: KeysetRequest to ScrollPosition. The cursor's string parts are decoded with the list's
 * KeysetKeys, in order, into the typed values Spring Data expects.
 *
 * Out: Window to KeysetPage. Spring Data hands back the sort values of the last row, as the
 * database returned them, which is exactly what a cursor must carry.
 */

/** The position to scroll from. No cursor means the start of the list. */
fun KeysetRequest.toScrollPosition(keys: KeysetKeys): ScrollPosition {
    val cursor = after ?: return ScrollPosition.keyset()
    val parts = cursor.parts()
    if (parts.size != keys.keys.size) {
        throw PaginationException("after", "does not belong to this list")
    }
    val values = keys.keys.zip(parts).associate { (key, part) ->
        key.property to try {
            key.decode(part)
        } catch (e: RuntimeException) {
            throw PaginationException("after", "is not a valid cursor")
        }
    }
    return ScrollPosition.forward(values)
}

/** The page, with the next cursor built from the last row's sort values. Null cursor on the last page. */
fun <T : Any> Window<T>.toKeysetPage(request: KeysetRequest, keys: KeysetKeys): KeysetPage<T> {
    val nextCursor = if (hasNext()) cursorAt(size() - 1, keys) else null
    return KeysetPage(content, request, nextCursor)
}

private fun <T : Any> Window<T>.cursorAt(index: Int, keys: KeysetKeys): Cursor {
    val position = positionAt(index) as KeysetScrollPosition
    val parts = keys.keys.map { key ->
        val value = position.keys[key.property]
            ?: error("the query was not sorted by '${key.property}'; sort with KeysetKeys.sort()")
        key.encodeAny(value)
    }
    return Cursor.of(parts)
}
