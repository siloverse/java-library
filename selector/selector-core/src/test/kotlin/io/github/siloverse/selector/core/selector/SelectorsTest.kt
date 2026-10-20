package io.github.siloverse.selector.core.selector

import io.github.siloverse.selector.core.error.SelectorException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SelectorsTest {

    class Order

    private fun parse(vararg params: Pair<String, String>) =
        Selectors.parse(Order::class, params.groupBy({ it.first }, { it.second }))

    @Test
    fun `three parts are namespace and attribute, four add a constraint`() {
        val selectors = parse("filter.order.id" to "42", "filter.order.checked-out-at.after" to "2026-01-01T00:00:00Z")

        assertThat(selectors).containsExactly(
            Selector(Order::class, "order", "id", null, listOf("42")),
            Selector(Order::class, "order", "checked-out-at", "after", listOf("2026-01-01T00:00:00Z")),
        )
        assertThat(selectors[1].key).isEqualTo("filter.order.checked-out-at.after")
    }

    @Test
    fun `a repeated key gives one selector with several values`() {
        assertThat(parse("filter.order.id" to "1", "filter.order.id" to "2").single().values).containsExactly("1", "2")
    }

    @Test
    fun `keys that are not filters are not looked at`() {
        assertThat(parse("page" to "1", "size" to "10", "after" to "x", "sort" to "price,desc", "anything" to "y")).isEmpty()
    }

    @Test
    fun `a malformed filter key is a client error, never ignored`() {
        for (bad in listOf("filter", "filter.order", "filter.order.id.gt.extra", "filter..id", "filter.order.")) {
            assertThatThrownBy { parse(bad to "1") }
                .isInstanceOf(SelectorException::class.java)
                .hasFieldOrPropertyWithValue("parameter", bad)
                .hasMessageContaining("is not 'filter.<namespace>.<attribute>'")
        }
    }
}
