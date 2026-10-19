package io.github.siloverse.pagination.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class PaginationPolicyTest {

    private val policy = PaginationPolicy(defaultSize = 20, maxSize = 100)

    @Test
    fun `missing page and size fall back to page zero and the default size`() {
        assertThat(policy.offset(page = null, size = null)).isEqualTo(OffsetRequest(0, 20))
    }

    @Test
    fun `given values are used as they are`() {
        assertThat(policy.offset(page = 3, size = 50)).isEqualTo(OffsetRequest(3, 50))
    }

    @Test
    fun `size at the maximum is allowed`() {
        assertThat(policy.offset(page = 0, size = 100).size).isEqualTo(100)
    }

    @Test
    fun `size above the maximum is rejected, not clamped`() {
        assertThatThrownBy { policy.offset(page = 0, size = 101) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "size")
            .hasMessageContaining("at most 100")
    }

    @Test
    fun `size below one is rejected`() {
        assertThatThrownBy { policy.offset(page = 0, size = 0) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "size")
    }

    @Test
    fun `negative page is rejected`() {
        assertThatThrownBy { policy.offset(page = -1, size = null) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "page")
    }

    @Test
    fun `keyset without a cursor starts from the beginning`() {
        val request = policy.keyset(after = null, size = null)

        assertThat(request.isFirstPage).isTrue()
        assertThat(request.size).isEqualTo(20)
    }

    @Test
    fun `keyset accepts a cursor we gave out`() {
        val cursor = Cursor.of("2026-10-19T10:00:00Z", "42")

        assertThat(policy.keyset(after = cursor.value, size = 10).after).isEqualTo(cursor)
    }

    @Test
    fun `keyset rejects a cursor the client made up`() {
        assertThatThrownBy { policy.keyset(after = "not base64 at all!", size = 10) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "after")
    }

    @Test
    fun `keyset rejects a blank cursor`() {
        assertThatThrownBy { policy.keyset(after = " ", size = 10) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "after")
    }

    @Test
    fun `keyset size is checked the same way`() {
        assertThatThrownBy { policy.keyset(after = null, size = 101) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "size")
    }

    @Test
    fun `a policy with a default above its maximum is a configuration bug`() {
        assertThatThrownBy { PaginationPolicy(defaultSize = 200, maxSize = 100) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `the built in default is twenty per page, one hundred at most`() {
        assertThat(PaginationPolicy.DEFAULT).isEqualTo(PaginationPolicy(20, 100))
    }
}
