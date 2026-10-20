package io.github.siloverse.selector.core.definition

import io.github.siloverse.selector.core.sort.Direction
import io.github.siloverse.selector.core.sort.SortOrder
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ListDefinitionTest {

    private val id = Field("id", FieldType.UUID, operators = setOf(Operator.EQ, Operator.IN), sortable = true)
    private val price = Field("price", FieldType.LONG, operators = setOf(Operator.LT, Operator.GT), sortable = true)
    private val title = Field("title", FieldType.STRING, operators = setOf(Operator.LIKE))

    @Test
    fun `a list knows its fields by name`() {
        val listing = ListDefinition(id, price, title, tieBreaker = "id")

        assertThat(listing.field("price")).isSameAs(price)
        assertThat(listing.field("nope")).isNull()
        assertThat(listing.sortableFields).containsExactly("id", "price")
    }

    @Test
    fun `the default sort is the tie breaker alone when none is declared`() {
        val listing = ListDefinition(id, price, tieBreaker = "id")

        assertThat(listing.defaultOrders).containsExactly(SortOrder("id", Direction.ASC))
    }

    @Test
    fun `a declared default sort gets the tie breaker appended`() {
        val listing = ListDefinition(id, price, tieBreaker = "id", defaultSort = listOf(SortOrder("price", Direction.DESC)))

        assertThat(listing.defaultOrders).containsExactly(SortOrder("price", Direction.DESC), SortOrder("id", Direction.ASC))
    }

    @Test
    fun `a field name may not appear twice`() {
        assertThatThrownBy { ListDefinition(id, Field("id", FieldType.LONG, sortable = true), tieBreaker = "id") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("twice")
    }

    @Test
    fun `the tie breaker must be a declared, sortable field`() {
        assertThatThrownBy { ListDefinition(id, price, tieBreaker = "created") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not a declared field")
        assertThatThrownBy { ListDefinition(id, title, tieBreaker = "title") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must be sortable")
    }

    @Test
    fun `a default sort may only name sortable fields`() {
        assertThatThrownBy { ListDefinition(id, title, tieBreaker = "id", defaultSort = listOf(SortOrder("title"))) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not sortable")
    }

    @Test
    fun `a field that allows nothing is a mistake`() {
        assertThatThrownBy { Field("dead", FieldType.STRING) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("neither")
    }

    @Test
    fun `a field name cannot contain the characters the wire format uses`() {
        assertThatThrownBy { Field("price.lt", FieldType.LONG, sortable = true) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Field("a,b", FieldType.LONG, sortable = true) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `field types parse the client's text once, and name what went wrong`() {
        assertThat(FieldType.LONG.parse("42")).isEqualTo(42L)
        assertThat(FieldType.BOOLEAN.parse("TRUE")).isTrue()
        assertThat(FieldType.UUID.parse("123e4567-e89b-12d3-a456-426614174000").toString()).isEqualTo("123e4567-e89b-12d3-a456-426614174000")
        assertThatThrownBy { FieldType.LONG.parse("forty") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("'forty' is not a valid long")
        assertThatThrownBy { FieldType.BOOLEAN.parse("yes") }
            .hasMessage("'yes' is not a valid boolean")
    }
}
