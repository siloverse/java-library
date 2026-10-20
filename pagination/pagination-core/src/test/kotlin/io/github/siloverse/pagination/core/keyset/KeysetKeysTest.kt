package io.github.siloverse.pagination.core.keyset

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.sort.Direction
import io.github.siloverse.pagination.core.sort.SortContract
import io.github.siloverse.pagination.core.sort.SortRegistry
import io.github.siloverse.pagination.core.sort.SortableFields
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

        assertThat(cursor.parts().first()).isEqualTo("createdAt:asc,id:asc")
        assertThat(keys.decode(cursor)).containsExactly(at, id)
    }

    @Test
    fun `a cursor made for another sort is rejected, naming both`() {
        val byPrice = KeysetKeys(KeysetKey.long("price", Direction.DESC), KeysetKey.uuid("id"))
        val cursor = byPrice.cursorOf(listOf(10L, UUID.randomUUID()))

        assertThatThrownBy { keys.decode(cursor) }
            .isInstanceOf(PaginationException::class.java)
            .hasMessage("after: was made for sort 'price:desc,id:asc', not 'createdAt:asc,id:asc'")
    }

    @Test
    fun `a registry holds one contract per entity`() {
        val contract = object : SortContract {
            override val entityType = String::class
            override val sortable = SortableFields(KeysetKey.uuid("id"), tieBreaker = "id")
        }

        assertThat(SortRegistry(listOf(contract)).sortableFor(String::class)).isSameAs(contract.sortable)
        assertThat(SortRegistry(listOf(contract)).sortableFor(Int::class)).isNull()
        assertThatThrownBy { SortRegistry(listOf(contract, contract)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("2 sort contracts for String")
    }

    @Test
    fun `a cursor with the wrong number of parts does not belong to this list`() {
        assertThatThrownBy { keys.decode(Cursor.of("createdAt:asc,id:asc", "only-one")) }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "after")
            .hasMessageContaining("does not belong")
    }

    @Test
    fun `a cursor whose part cannot be decoded is invalid`() {
        assertThatThrownBy { keys.decode(Cursor.of("createdAt:asc,id:asc", "not a date", UUID.randomUUID().toString())) }
            .isInstanceOf(PaginationException::class.java)
            .hasMessageContaining("not a valid cursor")
    }

    @Test
    fun `keys need at least one column and no duplicates`() {
        assertThatThrownBy { KeysetKeys(emptyList()) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { KeysetKeys(KeysetKey.uuid("id"), KeysetKey.uuid("id")) }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
