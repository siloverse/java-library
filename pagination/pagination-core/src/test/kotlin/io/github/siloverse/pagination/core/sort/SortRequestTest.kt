package io.github.siloverse.pagination.core.sort

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.keyset.KeysetKey
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SortRequestTest {

    private val listing = SortableFields(
        KeysetKey.uuid("id"), KeysetKey.long("price"), KeysetKey.offsetDateTime("createdAt"),
        tieBreaker = "id",
        defaultSort = listOf(SortOrder("createdAt", Direction.DESC)),
    )

    private fun parse(vararg raw: String) = SortRequest.parse(listing, raw.toList()).orders

    @Test
    fun `no sort means the list's default, tie breaker last`() {
        assertThat(SortRequest.parse(listing, null).orders)
            .containsExactly(SortOrder("createdAt", Direction.DESC), SortOrder("id", Direction.ASC))
        assertThat(SortRequest.parse(listing, emptyList()).orders)
            .containsExactly(SortOrder("createdAt", Direction.DESC), SortOrder("id", Direction.ASC))
    }

    @Test
    fun `a field alone sorts ascending, and the tie breaker is appended`() {
        assertThat(parse("price")).containsExactly(SortOrder("price", Direction.ASC), SortOrder("id", Direction.ASC))
    }

    @Test
    fun `direction follows the field after a comma, any case`() {
        assertThat(parse("price,desc")).startsWith(SortOrder("price", Direction.DESC))
        assertThat(parse("price,DESC")).startsWith(SortOrder("price", Direction.DESC))
        assertThat(parse("price, asc")).startsWith(SortOrder("price", Direction.ASC))
    }

    @Test
    fun `several sort parameters keep their order`() {
        assertThat(parse("price,desc", "createdAt"))
            .containsExactly(SortOrder("price", Direction.DESC), SortOrder("createdAt", Direction.ASC), SortOrder("id", Direction.ASC))
    }

    @Test
    fun `the client may name the tie breaker itself, as the last field, with its own direction`() {
        assertThat(parse("price,desc", "id,desc"))
            .containsExactly(SortOrder("price", Direction.DESC), SortOrder("id", Direction.DESC))
        assertThat(parse("id,desc")).containsExactly(SortOrder("id", Direction.DESC))
    }

    @Test
    fun `the tie breaker anywhere but last makes later fields pointless, so it is rejected`() {
        assertThatThrownBy { parse("id", "price") }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "sort")
            .hasMessageContaining("'id' must be the last sort field")
    }

    @Test
    fun `an unknown field is a client error naming sort`() {
        assertThatThrownBy { parse("colour") }
            .isInstanceOf(PaginationException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "sort")
            .hasMessageContaining("'colour' is not sortable")
    }

    @Test
    fun `a field that is not sortable is rejected, and the message lists what is`() {
        assertThatThrownBy { parse("title") }
            .isInstanceOf(PaginationException::class.java)
            .hasMessageContaining("'title' is not sortable")
            .hasMessageContaining("[id, price, createdAt]")
    }

    @Test
    fun `field names are case sensitive`() {
        assertThatThrownBy { parse("Price") }
            .isInstanceOf(PaginationException::class.java)
            .hasMessageContaining("'Price' is not sortable")
    }

    @Test
    fun `a bad direction is rejected`() {
        assertThatThrownBy { parse("price,down") }
            .isInstanceOf(PaginationException::class.java)
            .hasMessageContaining("'down' is not 'asc' or 'desc'")
    }

    @Test
    fun `a field twice is rejected`() {
        assertThatThrownBy { parse("price", "price,desc") }
            .isInstanceOf(PaginationException::class.java)
            .hasMessageContaining("'price' appears twice")
    }

    @Test
    fun `malformed entries are rejected`() {
        assertThatThrownBy { parse("") }.isInstanceOf(PaginationException::class.java)
        assertThatThrownBy { parse("price,desc,extra") }.isInstanceOf(PaginationException::class.java)
        assertThatThrownBy { parse(",desc") }.isInstanceOf(PaginationException::class.java)
    }

    @Test
    fun `the result is always total, the last order is the tie breaker`() {
        listOf(parse(), parse("price"), parse("createdAt,desc", "price"), parse("id,desc")).forEach { orders ->
            assertThat(orders.last().field).isEqualTo("id")
        }
    }

    @Test
    fun `the contract checks itself`() {
        assertThatThrownBy { SortableFields(KeysetKey.long("price"), tieBreaker = "id") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("tie breaker 'id' is not a sortable field")
        assertThatThrownBy { SortableFields(KeysetKey.uuid("id"), KeysetKey.uuid("id"), tieBreaker = "id") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("twice")
        assertThatThrownBy { SortableFields(KeysetKey.uuid("id"), tieBreaker = "id", defaultSort = listOf(SortOrder("price"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not sortable")
    }

    @Test
    fun `the keys for a sort carry the client's directions, tie breaker last`() {
        val keys = listing.keysFor(SortRequest.parse(listing, listOf("price,desc")))

        assertThat(keys.properties).containsExactly("price", "id")
        assertThat(keys.keys.map { it.direction }).containsExactly(Direction.DESC, Direction.ASC)
        assertThat(keys.sameDirection).isFalse()
        assertThat(listing.keysFor(SortRequest.parse(listing, listOf("price", "createdAt"))).sameDirection).isTrue()
    }
}
