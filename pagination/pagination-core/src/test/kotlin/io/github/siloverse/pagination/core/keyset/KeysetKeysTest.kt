package io.github.siloverse.pagination.core.keyset

import io.github.siloverse.pagination.core.error.PaginationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class KeysetKeysTest {

    private val keys = KeysetKeys(KeysetKey.offsetDateTime("createdAt"), KeysetKey.uuid("id"))

    @Test
    fun `a cursor round trips through the keys as typed values`() {
        val at = OffsetDateTime.parse("2026-10-20T10:00:00.123456Z")
        val id = UUID.randomUUID()

        val cursor = keys.cursorOf(listOf(at, id))

        assertThat(keys.decode(cursor)).containsExactly(at, id)
    }

    @Test
    fun `a cursor with the wrong number of parts does not belong to this list`() {
        assertThatThrownBy { keys.decode(Cursor.of("only-one")) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "after")
            .hasMessageContaining("does not belong")
    }

    @Test
    fun `a cursor whose part cannot be decoded is invalid`() {
        assertThatThrownBy { keys.decode(Cursor.of("not a date", UUID.randomUUID().toString())) }
            .isInstanceOf(PaginationException::class.java)
            .hasMessageContaining("not a valid cursor")
    }

    @Test
    fun `keys need at least one column and no duplicates`() {
        assertThatThrownBy { KeysetKeys(emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { KeysetKeys(KeysetKey.uuid("id"), KeysetKey.uuid("id")) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
