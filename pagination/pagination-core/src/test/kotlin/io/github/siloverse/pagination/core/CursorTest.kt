package io.github.siloverse.pagination.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CursorTest {

    @Test
    fun `round trips its parts in order`() {
        val cursor = Cursor.of("2026-10-19T10:00:00Z", "42")

        assertThat(cursor.parts()).containsExactly("2026-10-19T10:00:00Z", "42")
    }

    @Test
    fun `a part may contain the separator and any character`() {
        val cursor = Cursor.of("a.b", "x/y+z=", "")

        assertThat(cursor.parts()).containsExactly("a.b", "x/y+z=", "")
    }

    @Test
    fun `wire form is url safe`() {
        val cursor = Cursor.of("hello world?", "x/y+z")

        assertThat(cursor.value).matches("[A-Za-z0-9_.-]*")
    }

    @Test
    fun `equal parts give equal cursors`() {
        assertThat(Cursor.of("1", "2")).isEqualTo(Cursor.of("1", "2"))
        assertThat(Cursor.of("1", "2")).isNotEqualTo(Cursor.of("2", "1"))
    }

    @Test
    fun `needs at least one part`() {
        assertThatThrownBy { Cursor.of(emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `blank wire value is rejected`() {
        assertThatThrownBy { Cursor(" ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
