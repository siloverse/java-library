package io.github.siloverse.selector.core.definition

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class FilterDefinitionTest {

    private val id = Field("id", FieldType.UUID, Operator.EQ, Operator.IN)
    private val price = Field("price", FieldType.LONG, Operator.LT, Operator.GT)
    private val title = Field("title", FieldType.STRING, Operator.LIKE)

    @Test
    fun `a definition knows its fields by name`() {
        val listing = FilterDefinition(id, price, title)

        assertThat(listing.field("price")).isSameAs(price)
        assertThat(listing.field("nope")).isNull()
        assertThat(listing.fieldNames).containsExactly("id", "price", "title")
    }

    @Test
    fun `a field name may not appear twice`() {
        assertThatThrownBy { FilterDefinition(id, Field("id", FieldType.LONG, Operator.EQ)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("twice")
    }

    @Test
    fun `a definition needs at least one field`() {
        assertThatThrownBy { FilterDefinition(emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a field with no operator allows nothing and is a mistake`() {
        assertThatThrownBy { Field("dead", FieldType.STRING, emptySet()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("no operator")
    }

    @Test
    fun `a field name cannot contain the characters the wire format uses`() {
        assertThatThrownBy { Field("price.lt", FieldType.LONG, Operator.EQ) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Field("a,b", FieldType.LONG, Operator.EQ) }
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
