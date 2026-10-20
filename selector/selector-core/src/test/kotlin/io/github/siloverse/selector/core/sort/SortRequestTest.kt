package io.github.siloverse.selector.core.sort

import io.github.siloverse.selector.core.definition.Field
import io.github.siloverse.selector.core.definition.FieldType
import io.github.siloverse.selector.core.definition.ListDefinition
import io.github.siloverse.selector.core.definition.Operator
import io.github.siloverse.selector.core.error.SelectorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SortRequestTest {

    private val listing = ListDefinition(
        Field("id", FieldType.UUID, sortable = true),
        Field("price", FieldType.LONG, sortable = true),
        Field("createdAt", FieldType.OFFSET_DATE_TIME, sortable = true),
        Field("title", FieldType.STRING, operators = setOf(Operator.LIKE)),
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
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "sort")
            .hasMessageContaining("'id' must be the last sort field")
    }

    @Test
    fun `an unknown field is a client error naming sort`() {
        assertThatThrownBy { parse("colour") }
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "sort")
            .hasMessageContaining("unknown field 'colour'")
    }

    @Test
    fun `a field that exists but is not sortable is rejected, and the message lists what is`() {
        assertThatThrownBy { parse("title") }
            .isInstanceOf(SelectorException::class.java)
            .hasMessageContaining("'title' is not sortable")
            .hasMessageContaining("[id, price, createdAt]")
    }

    @Test
    fun `field names are case sensitive`() {
        assertThatThrownBy { parse("Price") }
            .isInstanceOf(SelectorException::class.java)
            .hasMessageContaining("unknown field 'Price'")
    }

    @Test
    fun `a bad direction is rejected`() {
        assertThatThrownBy { parse("price,down") }
            .isInstanceOf(SelectorException::class.java)
            .hasMessageContaining("'down' is not 'asc' or 'desc'")
    }

    @Test
    fun `a field twice is rejected`() {
        assertThatThrownBy { parse("price", "price,desc") }
            .isInstanceOf(SelectorException::class.java)
            .hasMessageContaining("'price' appears twice")
    }

    @Test
    fun `malformed entries are rejected`() {
        assertThatThrownBy { parse("") }.isInstanceOf(SelectorException::class.java)
        assertThatThrownBy { parse("price,desc,extra") }.isInstanceOf(SelectorException::class.java)
        assertThatThrownBy { parse(",desc") }.isInstanceOf(SelectorException::class.java)
    }

    @Test
    fun `the result is always total, the last order is the tie breaker`() {
        listOf(parse(), parse("price"), parse("createdAt,desc", "price"), parse("id,desc")).forEach { orders ->
            assertThat(orders.last().field).isEqualTo("id")
        }
    }
}
