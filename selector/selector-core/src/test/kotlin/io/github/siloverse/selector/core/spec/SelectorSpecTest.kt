package io.github.siloverse.selector.core.spec

import io.github.siloverse.selector.core.error.SelectorException
import io.github.siloverse.selector.core.selector.Selector
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class SelectorSpecTest {

    class Order

    class OrderIdSpec : SelectorSpec<Long>(Order::class, "order", "id", type = FieldType.LONG, arity = Arity.MULTI)
    class OrderPriceLtSpec : SelectorSpec<Long>(Order::class, "order", "price", "lt", FieldType.LONG, Arity.SINGLE)
    class OrderUuidSpec : SelectorSpec<UUID>(Order::class, "order", "uuid", type = FieldType.UUID, arity = Arity.SINGLE)

    private fun selector(key: String, vararg values: String): Selector {
        val parts = key.split(".")
        return Selector(Order::class, parts[1], parts[2], parts.getOrNull(3), values.toList())
    }

    @Test
    fun `a spec knows its key`() {
        assertThat(OrderIdSpec().key).isEqualTo("filter.order.id")
        assertThat(OrderPriceLtSpec().key).isEqualTo("filter.order.price.lt")
    }

    @Test
    fun `values are parsed once by the spec's type`() {
        assertThat(OrderIdSpec().parse(selector("filter.order.id", "1", " 2 "))).containsExactly(1L, 2L)
        assertThat(OrderPriceLtSpec().parse(selector("filter.order.price.lt", "50"))).containsExactly(50L)
    }

    @Test
    fun `a single-value spec refuses more than one value`() {
        assertThatThrownBy { OrderPriceLtSpec().parse(selector("filter.order.price.lt", "1", "2")) }
            .isInstanceOf(SelectorException::class.java)
            .hasMessage("filter.order.price.lt: takes exactly one value, got 2")
    }

    @Test
    fun `no value and empty values are refused`() {
        assertThatThrownBy { OrderIdSpec().parse(selector("filter.order.id")) }
            .hasMessage("filter.order.id: has no value")
        assertThatThrownBy { OrderIdSpec().parse(selector("filter.order.id", "1", "")) }
            .hasMessage("filter.order.id: has an empty value")
    }

    @Test
    fun `text the type cannot read is refused with the type's message`() {
        assertThatThrownBy { OrderUuidSpec().parse(selector("filter.order.uuid", "nope")) }
            .isInstanceOf(SelectorException::class.java)
            .hasMessage("filter.order.uuid: 'nope' is not a valid uuid")
    }

    @Test
    fun `the registry finds a spec by entity and key, and lists the entity's filters on a miss`() {
        val registry = SelectorRegistry(listOf(OrderIdSpec(), OrderPriceLtSpec()))

        assertThat(registry.specFor(selector("filter.order.id", "1"))).isInstanceOf(OrderIdSpec::class.java)
        assertThatThrownBy { registry.specFor(selector("filter.order.prize", "1")) }
            .isInstanceOf(SelectorException::class.java)
            .hasMessage("filter.order.prize: unknown filter; filters for Order: [filter.order.id, filter.order.price.lt]")
    }

    @Test
    fun `two specs for one key on one entity is a startup error`() {
        assertThatThrownBy { SelectorRegistry(listOf(OrderIdSpec(), OrderIdSpec())) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("2 specs for Order filter.order.id")
    }

    @Test
    fun `field types parse the client's text once, and name what went wrong`() {
        assertThat(FieldType.LONG.parse("42")).isEqualTo(42L)
        assertThat(FieldType.BOOLEAN.parse("TRUE")).isTrue()
        assertThatThrownBy { FieldType.LONG.parse("forty") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("'forty' is not a valid long")
        assertThat(FieldType("status") { it.uppercase() }.parse("open")).isEqualTo("OPEN")
    }
}
