package io.github.siloverse.pagination.spring.keyset

import io.github.siloverse.pagination.core.keyset.Cursor
import io.github.siloverse.pagination.core.keyset.KeysetKeys
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
    return ScrollPosition.forward(keys.properties.zip(keys.decode(cursor)).toMap())
}

/** The page, with the next cursor built from the last row's sort values. Null cursor on the last page. */
fun <T : Any> Window<T>.toKeysetPage(request: KeysetRequest, keys: KeysetKeys): KeysetPage<T> {
    val nextCursor = if (hasNext()) cursorAt(size() - 1, keys) else null
    return KeysetPage(content, request, nextCursor)
}

private fun <T : Any> Window<T>.cursorAt(index: Int, keys: KeysetKeys): Cursor {
    val position = positionAt(index) as KeysetScrollPosition
    val values = keys.properties.map { property ->
        position.keys[property] ?: error("the query was not sorted by '$property'; sort with keys.toSort()")
    }
    return keys.cursorOf(values)
}
