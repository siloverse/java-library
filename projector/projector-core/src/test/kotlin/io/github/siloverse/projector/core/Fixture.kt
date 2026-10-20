package io.github.siloverse.projector.core

import java.util.UUID

/** A tiny domain: orders with items. The "repository" counts its calls so a test can prove batching. */

class Order(val id: UUID = UUID.randomUUID(), val customer: String = "ada")
class Item(val orderId: UUID, val name: String)

class ItemRepository(private val items: List<Item>) {
    var calls = 0
    fun findByOrderIds(ids: List<UUID>): List<Item> {
        calls++
        return items.filter { it.orderId in ids }
    }
}

/** Mutable DTO: projectable fields are vars, written in place. */
class OrderDto(val id: UUID) {
    @Projectable("items") var items: List<ItemDto>? = null
    @Projectable("item-count", isDefault = true) var itemCount: Int? = null
    @Projectable("customer") var customer: String? = null
}

/** Immutable DTO: a data class, rebuilt through its primary constructor. */
data class OrderView(
    val id: UUID,
    @Projectable("items") val items: List<ItemDto>? = null,
    @Projectable("item-count", isDefault = true) val itemCount: Int? = null,
)

data class ItemDto(val name: String, @Projectable("order") val order: OrderDto? = null)

/** Batched: one repository call for the whole page. */
class ItemsProjector(private val repository: ItemRepository) : Projector<Order, List<ItemDto>>(Order::class, "items") {
    override fun project(entities: List<Order>): Map<Order, List<ItemDto>> {
        val byOrder = repository.findByOrderIds(entities.map { it.id }).groupBy { it.orderId }
        return entities.associateWith { order -> byOrder[order.id].orEmpty().map { ItemDto(it.name) } }
    }
}

class ItemCountProjector(private val repository: ItemRepository) : Projector<Order, Int>(Order::class, "item-count") {
    override fun project(entities: List<Order>): Map<Order, Int> {
        val counts = repository.findByOrderIds(entities.map { it.id }).groupingBy { it.orderId }.eachCount()
        return entities.associateWith { counts[it.id] ?: 0 }
    }
}

/** Already loaded: no batching needed. */
class CustomerProjector : SimpleProjector<Order, String>(Order::class, "customer") {
    override fun project(entity: Order): String = entity.customer.uppercase()
}
