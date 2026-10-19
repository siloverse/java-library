package io.github.siloverse.pagination.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 23 items with unique keys 1..23, sorted by key. The cursor is the key of the last row seen.
 */
class KeysetPageTest {

    private val items = (1..23).toMutableList()

    private fun cursorOf(item: Int) = Cursor.of(item.toString())

    private fun keyOf(cursor: Cursor) = cursor.parts().single().toInt()

    /** Stands in for: WHERE key > ? ORDER BY key LIMIT size + 1. */
    private fun fetch(request: KeysetRequest): KeysetPage<Int> {
        val after = request.after?.let(::keyOf) ?: Int.MIN_VALUE
        val rows = items.sorted().filter { it > after }.take(request.size + 1)
        return KeysetPage.fromOverFetch(rows, request, ::cursorOf)
    }

    @Test
    fun `first page starts from the beginning and bookmarks its last row`() {
        val page = fetch(KeysetRequest(after = null, size = 10))

        assertThat(page.items).containsExactlyElementsOf(1..10)
        assertThat(page.hasNext).isTrue()
        assertThat(page.nextCursor).isEqualTo(cursorOf(10))
    }

    @Test
    fun `walking the cursors visits every item once`() {
        val first = fetch(KeysetRequest(null, 10))
        val second = fetch(KeysetRequest(first.nextCursor, 10))
        val third = fetch(KeysetRequest(second.nextCursor, 10))

        assertThat(second.items).containsExactlyElementsOf(11..20)
        assertThat(third.items).containsExactly(21, 22, 23)
        assertThat(third.hasNext).isFalse()
        assertThat(third.nextCursor).isNull()
    }

    @Test
    fun `a row inserted between pages does not make the next page repeat a row`() {
        val first = fetch(KeysetRequest(null, 10))
        items.add(0) // a new row that sorts before everything the user has seen

        val offsetSecond = items.sorted().drop(10).take(10)
        val keysetSecond = fetch(KeysetRequest(first.nextCursor, 10))

        assertThat(offsetSecond).startsWith(10) // offset paging shows row 10 again
        assertThat(keysetSecond.items).containsExactlyElementsOf(11..20) // keyset does not
    }

    @Test
    fun `exactly size rows means no next page`() {
        val page = KeysetPage.fromOverFetch((1..10).toList(), KeysetRequest(null, 10), ::cursorOf)

        assertThat(page.items).hasSize(10)
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `no rows after the cursor gives an empty last page`() {
        val page = fetch(KeysetRequest(after = cursorOf(23), size = 10))

        assertThat(page.items).isEmpty()
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `more than size plus one rows is a caller bug`() {
        assertThatThrownBy { KeysetPage.fromOverFetch((1..12).toList(), KeysetRequest(null, 10), ::cursorOf) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("overfetch")
    }

    @Test
    fun `an empty page cannot claim a next page`() {
        assertThatThrownBy { KeysetPage(emptyList<Int>(), KeysetRequest(null, 10), nextCursor = cursorOf(1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `size below one is rejected`() {
        assertThatThrownBy { KeysetRequest(null, 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("size")
    }
}
