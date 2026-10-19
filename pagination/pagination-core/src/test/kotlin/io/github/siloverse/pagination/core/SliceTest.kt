package io.github.siloverse.pagination.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Same 23 items, size 10. A slice never counts. It learns whether a next page exists
 * by fetching one extra row.
 */
class SliceTest {

    private val items = (1..23).toList()

    /** Stands in for a database query with LIMIT size + 1. */
    private fun fetch(request: OffsetRequest): Slice<Int> =
        Slice.fromOverFetch(
            rows = items.drop(request.offset.toInt()).take(request.size + 1),
            request = request,
        )

    @Test
    fun `first slice holds ten items and knows a next page exists`() {
        val slice = fetch(OffsetRequest(page = 0, size = 10))

        assertThat(slice.items).containsExactlyElementsOf(1..10)
        assertThat(slice.hasPrevious).isFalse()
        assertThat(slice.hasNext).isTrue()
    }

    @Test
    fun `last slice holds the remaining three items and no next page`() {
        val slice = fetch(OffsetRequest(page = 2, size = 10))

        assertThat(slice.items).containsExactly(21, 22, 23)
        assertThat(slice.hasPrevious).isTrue()
        assertThat(slice.hasNext).isFalse()
    }

    @Test
    fun `slice past the end is empty, not an error`() {
        val slice = fetch(OffsetRequest(page = 3, size = 10))

        assertThat(slice.items).isEmpty()
        assertThat(slice.hasNext).isFalse()
    }

    @Test
    fun `exactly size rows means no next page`() {
        val slice = Slice.fromOverFetch(rows = (1..10).toList(), request = OffsetRequest(0, 10))

        assertThat(slice.items).hasSize(10)
        assertThat(slice.hasNext).isFalse()
    }

    @Test
    fun `size plus one rows means a next page, and the extra row is dropped`() {
        val slice = Slice.fromOverFetch(rows = (1..11).toList(), request = OffsetRequest(0, 10))

        assertThat(slice.items).containsExactlyElementsOf(1..10)
        assertThat(slice.hasNext).isTrue()
    }

    @Test
    fun `more than size plus one rows is a caller bug`() {
        assertThatThrownBy { Slice.fromOverFetch(rows = (1..12).toList(), request = OffsetRequest(0, 10)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("overfetch")
    }

    @Test
    fun `a slice cannot hold more items than its request size`() {
        assertThatThrownBy { Slice(items = (1..11).toList(), request = OffsetRequest(0, 10), hasNext = false) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
