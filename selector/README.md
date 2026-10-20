# siloverse selector

Turns a list endpoint's `filter.*` query parameters into a typed, validated WHERE clause.
Nothing else: sorting and paging belong to the pagination library.

**The rule, in one line:** every filter an entity offers is one declared spec; a query key that
matches none, a wrong value count, or a value the spec cannot read is a 400 naming the key,
never something silently ignored.

## 1. Dependencies

```kotlin
dependencies {
    implementation(platform("io.github.siloverse:selector-bom:0.1.0"))
    implementation("io.github.siloverse:selector-core")
    implementation("io.github.siloverse:selector-jdsl")     // the Kotlin JDSL engine
    implementation("io.github.siloverse:selector-spring")   // only in a Spring MVC service
}
```

## 2. Modules

| module | depends on | contents |
|---|---|---|
| `selector-core` | nothing | `Selector`, `Selectors.parse`, `SelectorSpec`, `FieldType`, `SelectorRegistry`, `SelectorException` |
| `selector-jdsl` | core, Kotlin JDSL | `JdslSpec`, `JdslSingleValueSpec`, `JdslMultiValueSpec`, `SelectorSpecManager` |
| `selector-spring` | core, Spring MVC | `SelectorConfiguration`, `@Selectors` + `SelectorsArgumentResolver`, 400 mapping |

## 3. The wire format

`filter.<namespace>.<attribute>` or `filter.<namespace>.<attribute>.<constraint>`. A repeated key
is one selector with several values. Keys without the `filter.` prefix are not filters.

| query | selector |
|---|---|
| `filter.order.status=open` | namespace `order`, attribute `status`, one value |
| `filter.order.checked-out-at.after=2026-01-01T00:00:00Z` | constraint `after` |
| `filter.order.id=1&filter.order.id=2` | two values |

## 4. Declare one spec per filter

A spec is a bean. It names its entity, its key, the type of its values and how many values it
takes, and it writes the predicate. The compiler hands it typed values; the `Jpql` receiver gives
it paths, subqueries and joins; repositories may be injected.

```kotlin
@Component
class OrderStatusSpec : JdslSingleValueSpec<String>(Order::class, "order", "status", type = FieldType.STRING) {
    override fun Jpql.predicate(value: String): Predicatable = path(Order::status).equal(value)
}

@Component
class OrderIdSpec : JdslMultiValueSpec<Long>(Order::class, "order", "id", type = FieldType.LONG) {
    override fun Jpql.predicate(values: List<Long>): Predicatable = path(Order::id).`in`(values)
}

@Component
class OrderAnyItemInStatusSpec : JdslMultiValueSpec<ItemStatus>(
    Order::class, "order", "any-item-status", type = FieldType("item status") { ItemStatus.valueOf(it) },
) {
    override fun Jpql.predicate(values: List<ItemStatus>): Predicatable = exists(
        select(intLiteral(1)).from(entity(OrderItem::class))
            .where(and(path(OrderItem::order).equal(entity(Order::class)), path(OrderItem::status).`in`(values)))
            .asSubquery(),
    )
}
```

`FieldType` has `STRING`, `INT`, `LONG`, `BOOLEAN`, `UUID`, `INSTANT`, `OFFSET_DATE_TIME`; make your own
with a name and a parser. The spec's `parse` is the only place client text becomes values.

## 5. The service

```kotlin
@Bean fun selectorSpecManager(specs: List<JdslSpec<*>>) = SelectorSpecManager(specs)   // once, in the app

fun orders(selectors: List<Selector>, request: KeysetRequest): KeysetPage<Order> =
    repository.findAll(limit = request.size + 1) {
        select(entity(Order::class)).from(entity(Order::class))
            .where(and(selectorSpecManager.apply(selectors), keysetAfter(request, orderSort)))
            .orderBy(*keysetOrder(request, orderSort).toTypedArray())
    }.toKeysetPage(request, orderSort)
```

`apply` ANDs the predicates and returns null for no selectors. Values are bound parameters, never
text in the JPQL. Two specs for one key on one entity stop the application at startup.

## 6. The controller

```kotlin
@Configuration
@Import(SelectorConfiguration::class)
class WebConfiguration

@GetMapping("/orders")
fun orders(@Selectors(entity = Order::class) selectors: List<Selector>, request: KeysetRequest): KeysetPageResponse<OrderDto>
```

The resolver hands the controller every `filter.*` key as a selector and refuses an unknown one
at the door. The pagination resolver reads the same `entity` to validate `sort=`, so the entity
is named once.

```json
{ "type": "about:blank", "title": "Invalid filter", "status": 400,
  "detail": "filter.order.prize: unknown filter; filters for Order: [filter.order.id, filter.order.status]",
  "parameter": "filter.order.prize" }
```

## 7. Tests

`selector-core` tests are plain unit tests; `selector-spring` runs a real Spring MVC context with
MockMvc. `selector-jdsl` runs against a real Postgres 17 in Testcontainers with the JDSL executor
wired by hand (no Boot), so building it needs Docker. `HqlTupleComparisonTest` there proves that a
JDSL `customPredicate` tuple comparison renders as a SQL row value that Postgres seeks on, which the
pagination library's JDSL keyset builds on.
