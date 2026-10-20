package io.github.siloverse.projector.core

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ProjectorManagerTest {

    private val orders = (1..25).map { Order(customer = "customer-$it") }
    private val items = orders.flatMapIndexed { i, order -> (1..(i % 3)).map { Item(order.id, "item-$it of ${order.customer}") } }
    private val repository = ItemRepository(items)
    private val manager = ProjectorManager(ProjectorRegistry(listOf(ItemsProjector(repository), ItemCountProjector(repository), CustomerProjector())))

    private fun dtos() = orders.map { OrderDto(it.id) }

    @Test
    fun `a page of 25 costs one repository call per active projector, not 25`() {
        val projected = manager.apply(Order::class, orders, dtos(), Projection(include = setOf("items")))

        assertThat(repository.calls).isEqualTo(2) // items + the default item-count
        assertThat(projected.map { it.items!!.size }).isEqualTo(orders.indices.map { it % 3 })
        assertThat(projected.map { it.itemCount }).isEqualTo(orders.indices.map { it % 3 })
    }

    @Test
    fun `defaults are on without include, and can be excluded`() {
        val withDefaults = manager.apply(Order::class, orders, dtos(), Projection.DEFAULTS)
        assertThat(withDefaults.first().itemCount).isNotNull()
        assertThat(withDefaults.first().items).isNull()
        assertThat(withDefaults.first().customer).isNull()

        val excluded = manager.apply(Order::class, orders, dtos(), Projection(exclude = setOf("item-count")))
        assertThat(excluded.first().itemCount).isNull()
        assertThat(repository.calls).isEqualTo(1) // only the first call projected anything
    }

    @Test
    fun `include switches a non-default part on`() {
        val projected = manager.apply(Order::class, orders, dtos(), Projection(include = setOf("customer"), exclude = setOf("item-count")))

        assertThat(projected.first().customer).isEqualTo("CUSTOMER-1")
        assertThat(repository.calls).isZero()
    }

    @Test
    fun `an immutable DTO is rebuilt through its primary constructor`() {
        val views = orders.map { OrderView(it.id) }

        val projected = manager.apply(Order::class, orders, views, Projection(include = setOf("items")))

        assertThat(projected).isNotSameAs(views)
        assertThat(projected.map { it.id }).isEqualTo(orders.map { it.id })
        assertThat(projected[2].items).hasSize(2)
        assertThat(projected[2].itemCount).isEqualTo(2)
        assertThat(views[2].items).isNull() // the originals are untouched
    }

    @Test
    fun `an unknown name is a client error that lists the DTO's projections`() {
        assertThatThrownBy { manager.apply(Order::class, orders, dtos(), Projection(include = setOf("itmes"))) }
            .isInstanceOf(ProjectorException::class.java)
            .hasFieldOrPropertyWithValue("parameter", "include")
            .hasMessage("include: unknown projection 'itmes'; projections of OrderDto: [items, item-count, customer]")
        assertThatThrownBy { manager.apply(Order::class, orders, dtos(), Projection(exclude = setOf("nope"))) }
            .hasFieldOrPropertyWithValue("parameter", "exclude")
    }

    @Test
    fun `a DTO field with no projector is an error naming the field and the fix`() {
        class LonelyDto(val id: UUID) { @Projectable("ghost") var ghost: String? = null }

        assertThatThrownBy { manager.apply(Order::class, orders.take(1), listOf(LonelyDto(orders[0].id)), Projection.DEFAULTS) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("LonelyDto.ghost is @Projectable(\"ghost\") but no Projector<Order, …> named 'ghost' exists")
    }

    @Test
    fun `a projector whose output does not fit the field is refused`() {
        class WrongDto(val id: UUID) { @Projectable("customer") var customer: Int? = null }

        assertThatThrownBy { manager.apply(Order::class, orders.take(1), listOf(WrongDto(orders[0].id)), Projection.DEFAULTS) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("CustomerProjector produces java.lang.String but WrongDto.customer is java.lang.Integer")
    }

    @Test
    fun `two projectors for one name on one entity is a startup error`() {
        assertThatThrownBy { ProjectorRegistry(listOf(CustomerProjector(), CustomerProjector())) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("2 projectors for Order.customer")
    }

    @Test
    fun `recursion stops at the projector's maxDepth`() {
        // items project their order, whose items project their order, ... ; the ItemsProjector allows depth 2
        lateinit var deep: ProjectorManager
        val itemsThatProjectBack = object : Projector<Order, List<ItemDto>>(Order::class, "items", maxDepth = 2) {
            override fun project(entities: List<Order>): Map<Order, List<ItemDto>> =
                entities.associateWith { order ->
                    val item = Item(order.id, "x")
                    val dto = deep.apply(Item::class, item, ItemDto(item.name), Projection(include = setOf("order")))
                    listOf(dto)
                }
        }
        val orderOfItem = object : Projector<Item, OrderDto>(Item::class, "order") {
            override fun project(entities: List<Item>): Map<Item, OrderDto> =
                entities.associateWith { item ->
                    val order = orders.first { it.id == item.orderId }
                    deep.apply(Order::class, order, OrderDto(order.id), Projection(include = setOf("items"), exclude = setOf("item-count")))
                }
        }
        deep = ProjectorManager(ProjectorRegistry(listOf(itemsThatProjectBack, orderOfItem, ItemCountProjector(repository), CustomerProjector())))

        val top = deep.apply(Order::class, orders[0], OrderDto(orders[0].id), Projection(include = setOf("items"), exclude = setOf("item-count")))

        val level1 = top.items!!.single().order!!   // items depth 1, order depth 1
        assertThat(level1.items).isNotNull()         // items depth 2: allowed, maxDepth = 2
        assertThat(level1.items!!.single().order).isNull()   // order depth 2: stopped, maxDepth = 1
    }

    @Test
    fun `projection parsing splits comma lists and refuses empty names`() {
        assertThat(Projection.parse(listOf("items,customer", "item-count"), null))
            .isEqualTo(Projection(include = setOf("items", "customer", "item-count")))
        assertThat(Projection.parse(null, null)).isEqualTo(Projection.DEFAULTS)
        assertThatThrownBy { Projection.parse(listOf("items,,customer"), null) }
            .isInstanceOf(ProjectorException::class.java)
            .hasMessage("include: has an empty name")
    }
}
