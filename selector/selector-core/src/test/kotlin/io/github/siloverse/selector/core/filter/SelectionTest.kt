package io.github.siloverse.selector.core.filter

import io.github.siloverse.selector.core.definition.Field
import io.github.siloverse.selector.core.definition.FieldType
import io.github.siloverse.selector.core.definition.FilterDefinition
import io.github.siloverse.selector.core.definition.Operator
import io.github.siloverse.selector.core.error.SelectorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class SelectionTest {

    private val listing = FilterDefinition(
        Field("id", FieldType.UUID, Operator.EQ, Operator.IN),
        Field("price", FieldType.LONG, Operator.EQ, Operator.LT, Operator.LTE, Operator.GT, Operator.GTE),
        Field("status", FieldType.STRING, Operator.EQ, Operator.NE, Operator.IN),
        Field("title", FieldType.STRING, Operator.LIKE),
        Field("active", FieldType.BOOLEAN, Operator.EQ),
    )

    private fun parse(vararg params: Pair<String, String>): Selection =
        Selection.parse(listing, params.groupBy({ it.first }, { it.second }))

    private fun one(vararg params: Pair<String, String>): Condition<*> = parse(*params).conditions.single()

    @Test
    fun `no filter params means no filter`() {
        val selection = parse()

        assertThat(selection.isEmpty).isTrue()
        assertThat(selection).isEqualTo(Selection.NONE)
    }

    @Test
    fun `a bare field is equals, typed by the field`() {
        val condition = one("price" to "50")

        assertThat(condition.field.name).isEqualTo("price")
        assertThat(condition.operator).isEqualTo(Operator.EQ)
        assertThat(condition.value).isEqualTo(50L)
    }

    @Test
    fun `the operator follows the field after a dot`() {
        val condition = one("price.lt" to "50")

        assertThat(condition.operator).isEqualTo(Operator.LT)
        assertThat(condition.value).isEqualTo(50L)
    }

    @Test
    fun `in takes a comma separated list, each value typed`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()

        val condition = one("id.in" to "$a, $b")

        assertThat(condition.operator).isEqualTo(Operator.IN)
        assertThat(condition.values).containsExactly(a, b)
    }

    @Test
    fun `several params become several conditions, in the order given`() {
        val selection = parse("status" to "open", "price.lte" to "100", "active" to "true")

        assertThat(selection.conditions.map { it.toString() }).containsExactly("status.eq=open", "price.lte=100", "active.eq=true")
    }

    @Test
    fun `paging and sort keys are not filters and are skipped`() {
        val selection = parse("page" to "2", "size" to "10", "after" to "abc", "sort" to "price,desc", "status" to "open")

        assertThat(selection.conditions).hasSize(1)
    }

    @Test
    fun `the reserved set can be replaced`() {
        val selection = Selection.parse(listing, mapOf("status" to listOf("open"), "cursor" to listOf("x")), reserved = setOf("cursor"))

        assertThat(selection.conditions).hasSize(1)
        assertThatThrownBy { Selection.parse(listing, mapOf("page" to listOf("2")), reserved = setOf("cursor")) }
            .isInstanceOf(SelectorException::class.java)
    }

    @Test
    fun `an unknown key is a client error, never silently ignored`() {
        assertThatThrownBy { parse("prodcut" to "1") }
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "prodcut")
            .hasMessageContaining("unknown filter field 'prodcut'")
            .hasMessageContaining("[id, price, status, title, active]")
    }

    @Test
    fun `an unknown operator is rejected`() {
        assertThatThrownBy { parse("price.between" to "1") }
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "price.between")
            .hasMessageContaining("unknown operator 'between'")
    }

    @Test
    fun `an operator the field does not allow is rejected, and the message lists what is`() {
        assertThatThrownBy { parse("title.lt" to "a") }
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "title.lt")
            .hasMessageContaining("'lt' is not allowed on 'title'")
            .hasMessageContaining("[like]")
    }

    @Test
    fun `a value that does not parse is rejected with the type's message`() {
        assertThatThrownBy { parse("price.lt" to "cheap") }
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "price.lt")
            .hasMessage("price.lt: 'cheap' is not a valid long")
        assertThatThrownBy { parse("active" to "yes") }
            .hasMessage("active: 'yes' is not a valid boolean")
    }

    @Test
    fun `one bad value in a list rejects the whole list`() {
        assertThatThrownBy { parse("id.in" to "${UUID.randomUUID()},not-a-uuid") }
            .isInstanceOf(SelectorException::class.java)
            .hasMessageContaining("'not-a-uuid' is not a valid uuid")
    }

    @Test
    fun `empty values are rejected`() {
        assertThatThrownBy { parse("status" to "") }.isInstanceOf(SelectorException::class.java).hasMessageContaining("empty value")
        assertThatThrownBy { parse("status.in" to "open,,closed") }.isInstanceOf(SelectorException::class.java).hasMessageContaining("empty value")
        assertThatThrownBy { parse("status.in" to "") }.isInstanceOf(SelectorException::class.java)
    }

    @Test
    fun `a key given twice is rejected`() {
        assertThatThrownBy { parse("price.lt" to "1", "price.lt" to "2") }
            .isInstanceOf(SelectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "price.lt")
            .hasMessageContaining("given 2 times")
    }

    @Test
    fun `a malformed key is rejected`() {
        assertThatThrownBy { parse("price.lt.extra" to "1") }.isInstanceOf(SelectorException::class.java)
        assertThatThrownBy { parse(".lt" to "1") }.isInstanceOf(SelectorException::class.java)
    }

    @Test
    fun `like is only declared on string fields`() {
        assertThatThrownBy { Field("price", FieldType.LONG, Operator.LIKE) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'like' only makes sense on a string field")
    }

    @Test
    fun `a condition carries exactly one value unless it is in`() {
        val price = listing.field("price")!!
        assertThatThrownBy { Condition(price as Field<Long>, Operator.LT, listOf(1L, 2L)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { Condition(price, Operator.LT, emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
