# siloverse openapi

Makes springdoc describe a list endpoint completely, so a client generated from the spec gets every
filter, the paging parameters, the sort, and the DTO's optional parts as typed Kotlin parameters.

**The rule, in one line:** the committed `openapi-spec.yaml` is the silo's contract; everything a
consumer may send is in it, named and typed, and nothing a resolver reads is left to springdoc's guess.

## 1. Dependencies

```kotlin
dependencies {
    implementation("io.github.siloverse:openapi-springdoc:0.1.0")
}
```

One module, so no BOM yet; one appears when a second module earns its existence.

`openapi-springdoc` depends on the released pagination, selector and projector libraries and on
`springdoc-openapi-starter-webmvc-api`. It is a consumer of those libraries, with its own release cadence.

## 2. What it adds to the spec

| controller parameter | spec parameters |
|---|---|
| `@Selectors(entity = Order::class) selectors: List<Selector>` | one query parameter per `SelectorSpec` of `Order`, named by its key (`filter.order.price.lt`), typed by its `FieldType` (`int64`, `uuid`, `date-time`, ...), an array for multi-value specs |
| `request: OffsetRequest` | `page` (min 0, default 0), `size` (min 1, max and default from the `PaginationPolicy`), `sort` when the entity has a `SortContract` |
| `request: KeysetRequest` | `after` (the opaque cursor), `size`, `sort` |
| `projection: Projection` | `include` and `exclude`, repeatable, an **enum** of the returned DTO's `@Projectable` names, defaults named in the description |

The `sort` description lists the sortable fields, the default order and the tie breaker. The DTO for
`include` is found in the return type through `ResponseEntity`, the pagination response models and
collections. springdoc is told to ignore `OffsetRequest`, `KeysetRequest`, `Projection` and `@Selectors`,
so it does not also document them as request objects.

## 3. Wiring

```kotlin
@Configuration
@Import(OpenApiConfiguration::class, PaginationConfiguration::class, SelectorConfiguration::class, ProjectorConfiguration::class)
class WebConfiguration
```

With Spring Boot the spec is at `/v3/api-docs` and `/v3/api-docs.yaml`. In a secured silo, permit
`/v3/api-docs/**` under the test profile.

## 4. Writing the contract

`OpenApiSpec.write(mockMvc, path)` fetches the YAML from the running test application and writes it,
returning whether the file changed. A silo's spec test writes `openapi-spec.yaml` at the repo root;
the build convention turns "changed" into a failed check, so the committed contract never drifts
from the code without a visible diff.

## 5. Tests

`OpenApiSpecTest` boots a real Spring Boot application (`ListingApp`) with one entity, four filter
specs, a sort contract, two projectors and three endpoints, reads `/v3/api-docs`, and asserts every
row of the table above against the JSON.
