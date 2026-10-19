package io.github.siloverse.pagination.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * 23 items, page size 10. Pages 0, 1 and 2 exist. Page 2 holds the last 3 items.
 * Page 3 is a legal request that simply has no items.
 */
class OffsetPageTest {

    private val items = (1..23).toList()

    /** Stands in for a database query: skip `offset` rows, take `size` rows. */
    private fun fetch(request: OffsetRequest): OffsetPage<Int> =
        OffsetPage(
            items = items.drop(request.offset.toInt()).take(request.size),
            request = request,
            totalElements = items.size.toLong(),
        )

    @Test
    fun `first page holds the first ten items`() {
        val page = fetch(OffsetRequest(page = 0, size = 10))

        assertThat(page.items).containsExactlyElementsOf(1..10)
        assertThat(page.totalElements).isEqualTo(23)
        assertThat(page.totalPages).isEqualTo(3)
        assertThat(page.hasPrevious).isFalse()
        assertThat(page.hasNext).isTrue()
    }

    @Test
    fun `middle page has both neighbours`() {
        val page = fetch(OffsetRequest(page = 1, size = 10))

        assertThat(page.items).containsExactlyElementsOf(11..20)
        assertThat(page.hasPrevious).isTrue()
        assertThat(page.hasNext).isTrue()
    }

    @Test
    fun `last page holds the remaining three items`() {
        val page = fetch(OffsetRequest(page = 2, size = 10))

        assertThat(page.items).containsExactly(21, 22, 23)
        assertThat(page.hasPrevious).isTrue()
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `page past the end is empty, not an error`() {
        val page = fetch(OffsetRequest(page = 3, size = 10))

        assertThat(page.items).isEmpty()
        assertThat(page.totalPages).isEqualTo(3)
        assertThat(page.hasPrevious).isTrue()
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `no items means zero pages`() {
        val page = OffsetPage(items = emptyList<Int>(), request = OffsetRequest(0, 10), totalElements = 0)

        assertThat(page.totalPages).isEqualTo(0)
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `offset is page times size`() {
        assertThat(OffsetRequest(page = 0, size = 10).offset).isEqualTo(0)
        assertThat(OffsetRequest(page = 2, size = 10).offset).isEqualTo(20)
    }

    @Test
    fun `negative page is rejected`() {
        assertThatThrownBy { OffsetRequest(page = -1, size = 10) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("page")
    }

    @Test
    fun `size below one is rejected`() {
        assertThatThrownBy { OffsetRequest(page = 0, size = 0) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("size")
    }

    @Test
    fun `a page cannot hold more items than its request size`() {
        assertThatThrownBy { OffsetPage(items = (1..11).toList(), request = OffsetRequest(0, 10), totalElements = 23) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
