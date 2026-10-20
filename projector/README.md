# siloverse projector

Optional parts of a response DTO, switched on by the client and computed in one batch per page.
`include=items` fills `items` for every order of the page with ONE extra query, not one per order.

**The rule, in one line:** a projector computes its part for the whole page at once; a name the DTO
does not offer is a 400 naming `include` or `exclude`; a DTO field with no projector, or a projector
that does not fit its field, stops the application, not a request.

## 1. Dependencies

```kotlin
dependencies {
    implementation(platform("io.github.siloverse:projector-bom:0.1.0"))
    implementation("io.github.siloverse:projector-core")
    implementation("io.github.siloverse:projector-spring")   // only in a Spring MVC service
}
```

## 2. Modules

| module | depends on | contents |
|---|---|---|
| `projector-core` | kotlin-reflect | `@Projectable`, `Projector` / `SimpleProjector`, `Projection`, `ProjectorRegistry`, `ProjectorManager`, `ProjectorException` |
| `projector-spring` | core, Spring MVC | `ProjectorConfiguration`, `Projection` argument resolver, 400 mapping |

## 3. Mark the DTO

```kotlin
data class OrderDto(
    val id: UUID,
    @Projectable("items") val items: List<ItemDto>? = null,
    @Projectable("item-count", isDefault = true) val itemCount: Int? = null,
)
```

A default part is on unless the client sends `exclude=item-count`. A data class is rebuilt through its
primary constructor; a class with `var` fields is written in place.

## 4. Declare one projector per part

```kotlin
@Component
class OrderItemsProjector(private val items: ItemRepository) : Projector<Order, List<ItemDto>>(Order::class, "items") {
    override fun project(entities: List<Order>): Map<Order, List<ItemDto>> {
        val byOrder = items.findByOrderIdIn(entities.map { it.id }).groupBy { it.orderId }   // one query
        return entities.associateWith { order -> byOrder[order.id].orEmpty().map(ItemDto::from) }
    }
}

@Component
class OrderCustomerProjector : SimpleProjector<Order, String>(Order::class, "customer") {   // already loaded: no batching needed
    override fun project(entity: Order): String = entity.customer.displayName
}
```

The projector is matched to the field by entity type and name. At the first projection of a DTO the
registry checks that every `@Projectable` field has a projector and that the projector's output type
is the field's type. Two projectors for one name on one entity stop the application at startup.

If a projected DTO projects back (item → order → item), `maxDepth` on the projector bounds it;
beyond the depth the field is left untouched.

## 5. The wire format

`include=items,customer` switches parts on; `exclude=item-count` switches a default off. Both repeatable.

```json
{ "type": "about:blank", "title": "Invalid projection", "status": 400,
  "detail": "include: unknown projection 'itmes'; projections of OrderDto: [items, item-count]", "parameter": "include" }
```

## 6. Controller and mapper

```kotlin
@Configuration
@Import(ProjectorConfiguration::class)
class WebConfiguration

@GetMapping("/orders")
fun orders(
    @Selectors(entity = Order::class) selectors: List<Selector>,
    request: KeysetRequest,
    projection: Projection,
): KeysetPageResponse<OrderDto> {
    val page = orderService.orders(selectors, request)
    return page.map(OrderDto::from)
        .let { dtos -> KeysetPage(projectorManager.apply(Order::class, page.items, dtos.items, projection), dtos.request, dtos.nextCursor) }
        .toResponse()
}
```

`apply` runs each active projector once for the whole page and returns the DTOs with their parts set.

## 7. Tests

Plain unit tests. The central one proves batching with a counting repository: a page of 25 orders
costs one call per active projector. `projector-spring` runs a real Spring MVC context with MockMvc.
