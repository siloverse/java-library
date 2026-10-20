# siloverse selector

Turns a list endpoint's query parameters into a typed, validated WHERE clause. Nothing else:
sorting and paging belong to the pagination library.

**The rule, in one line:** a list declares which fields a client may filter and how; anything
outside that declaration is a 400 naming the query key, never silently ignored.

## 1. Dependencies

```kotlin
dependencies {
    implementation(platform("io.github.siloverse:selector-bom:0.1.0"))
    implementation("io.github.siloverse:selector-core")
    implementation("io.github.siloverse:selector-jdsl")     // the Kotlin JDSL engine adapter
    implementation("io.github.siloverse:selector-spring")   // only in a Spring MVC service
}
```

## 2. Modules

| module | depends on | contents |
|---|---|---|
| `selector-core` | nothing | `FilterDefinition`, `Field`, `FieldType`, `Operator`, `Selection`, `Condition`, `SelectorException` |
| `selector-jdsl` | core, Kotlin JDSL | `JdslBindings`, `Jpql.where(selection, bindings)`, `JdslStrategy` |
| `selector-spring` | core, Spring MVC | `SelectorConfiguration`, `@Filtered` + `SelectionArgumentResolver`, 400 mapping |

## 3. Declare the list once

```kotlin
val id = Field("id", FieldType.UUID, Operator.EQ, Operator.IN)
val status = Field("status", FieldType.STRING, Operator.EQ, Operator.NE, Operator.IN)
val price = Field("price", FieldType.LONG, Operator.EQ, Operator.LT, Operator.LTE, Operator.GT, Operator.GTE)
val title = Field("title", FieldType.STRING, Operator.LIKE)
val search = Field("search", FieldType.STRING, Operator.EQ)        // no column: a strategy serves it

val listingFilters = FilterDefinition(id, status, price, title, search)
```

`like` may only be declared on a string field. A field with no operator is rejected.

## 4. The wire format

| query | meaning |
|---|---|
| `status=open` | equals |
| `price.lt=50` | any operator by suffix: `eq`, `ne`, `lt`, `lte`, `gt`, `gte`, `in`, `like` |
| `id.in=a,b,c` | a comma-separated list |
| `title.like=listing 1%` | the pattern is passed through; the client controls `%` |

`page`, `size`, `after` and `sort` are not filters and are skipped (the set is replaceable).

`Selection.parse(definition, params)` returns the typed conditions or throws `SelectorException(parameter, message)`:
unknown field (the message lists the fields), unknown or disallowed operator (lists what is allowed),
a value that does not parse (`price.lt: 'cheap' is not a valid long`), an empty value, a key given twice.

## 5. The Kotlin JDSL engine

Bind each field once. The compiler checks a `Field<Long>` against a `Long` property. Every field
must be bound, or the bindings refuse to build.

```kotlin
val bindings = JdslBindings<Listing>(listingFilters) {
    bind(id, Listing::id)
    bind(status, Listing::status)
    bind(price, Listing::price)
    bind(title, Listing::title)
    strategy(search) { condition -> path(Listing::title).like("%${condition.value}%") }
}

fun listings(selection: Selection): List<Listing> =
    repository.findAll {
        select(entity(Listing::class))
            .from(entity(Listing::class))
            .where(where(selection, bindings))          // null when the selection is empty
            .orderBy(path(Listing::createdAt).asc(), path(Listing::id).asc())
    }.filterNotNull()
```

Values travel as query parameters, never as text in the JPQL.

A `JdslStrategy` is the escape hatch for what a path comparison cannot say: a subquery, a join,
a computed column. It receives the typed `Condition` and returns any JDSL predicate.

### Keyset paging through JDSL

JDSL has no scrolling, but HQL 7 accepts a tuple comparison and Hibernate 7.4 renders it as a SQL
row value (`HqlTupleComparisonTest`):

```kotlin
customPredicate("({0}, {1}) > ({2}, {3})", path(Listing::createdAt), path(Listing::id), value(ts), value(id))
```

becomes `(l1_0.created_at,l1_0.id)>(?,?)`, which Postgres answers with an `Index Cond` seek on the
composite index. That is the fast keyset form, which Spring Data's keyset scrolling (OR chain, index walk
with a Filter) does not produce. The pagination library's JDSL adapter builds on this.

## 6. In a Spring MVC service

Import the configuration, declare one `FilterDefinition` bean per list, and name it on the
controller parameter. The resolver hands the controller a parsed `Selection`; a bad filter is a
400 problem detail naming the query key.

```kotlin
@Configuration
@Import(SelectorConfiguration::class)
class WebConfiguration {
    @Bean fun listingFilters() = FilterDefinition(id, status, price, title, search)
}

@GetMapping("/listings")
fun listings(@Filtered("listingFilters") selection: Selection, request: KeysetRequest): KeysetPageResponse<ListingDto> = ...
```

```json
{ "type": "about:blank", "title": "Invalid filter", "status": 400,
  "detail": "prodcut: unknown filter field 'prodcut'; fields: [id, status, price, title, search]", "parameter": "prodcut" }
```

## 7. Tests

`selector-core` tests are plain unit tests; `selector-spring` runs a real Spring MVC context with
MockMvc. `selector-jdsl` runs against a real Postgres 17 in Testcontainers with the JDSL executor
wired by hand (no Boot), so building it needs Docker.
