package io.github.siloverse.pagination.spring

import io.github.siloverse.pagination.core.OffsetRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort

class PageableAdaptersTest {

    private val request = OffsetRequest(page = 2, size = 10)

    @Test
    fun `request becomes a Pageable with the same page and size`() {
        val pageable = request.toPageable(Sort.by("createdAt").descending())

        assertThat(pageable.pageNumber).isEqualTo(2)
        assertThat(pageable.pageSize).isEqualTo(10)
        assertThat(pageable.offset).isEqualTo(20)
        assertThat(pageable.sort.getOrderFor("createdAt")!!.isDescending).isTrue()
    }

    @Test
    fun `Spring Data page becomes an OffsetPage with the total`() {
        val springPage = PageImpl((21..23).toList(), PageRequest.of(2, 10), 23)

        val page = springPage.toOffsetPage(request)

        assertThat(page.items).containsExactly(21, 22, 23)
        assertThat(page.totalElements).isEqualTo(23)
        assertThat(page.totalPages).isEqualTo(3)
        assertThat(page.hasNext).isFalse()
    }

    @Test
    fun `Spring Data slice becomes a Slice with the same hasNext`() {
        val springSlice = SliceImpl((21..30).toList(), PageRequest.of(2, 10), true)

        val slice = springSlice.toSlice(request)

        assertThat(slice.items).hasSize(10)
        assertThat(slice.hasNext).isTrue()
        assertThat(slice.hasPrevious).isTrue()
    }
}
