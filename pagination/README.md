# siloverse pagination

Request and result types for paging through a list, plus the policy that turns raw client
input into a request the service can trust. Offset, slice and keyset, in one shape.

**The rule, in one line:** the policy is the only door from client input to a request;
it rejects bad input, it never clamps it.

## 1. Dependencies

```kotlin
dependencies {
    implementation(platform("io.github.siloverse:pagination-bom:0.1.0"))
    implementation("io.github.siloverse:pagination-core")
    implementation("io.github.siloverse:pagination-spring")   // only in a Spring MVC service
}
```

Artifacts live on GitHub Packages (`https://maven.pkg.github.com/siloverse/java-library`); the
repository block and credentials are the same as for messaging.

## 2. Modules

| module | depends on | contents |
|---|---|---|
| `pagination-core` | nothing | `OffsetRequest` / `OffsetPage`, `Slice`, `KeysetRequest` / `KeysetPage`, `Cursor`, `PaginationPolicy`, `PaginationException` |
| `pagination-spring` | core, Spring MVC, Spring Data Commons | `PaginationConfiguration`, `PaginationArgumentResolver`, flat response models, `Pageable` adapters, `KeysetKeys` + scroll adapters, 400 mapping |
| `pagination-bom` | — | version alignment for consumers |

## 3. The three kinds of page

| kind | request | result | counts? | knows next page by | use when |
|---|---|---|---|---|---|
| offset | `OffsetRequest(page, size)` | `OffsetPage` | yes (`COUNT`) | total | small table, user wants page numbers |
| slice | `OffsetRequest(page, size)` | `Slice` | no | fetching `size + 1` rows | offset is fine but `COUNT` is too expensive |
| keyset | `KeysetRequest(after, size)` | `KeysetPage` | no | fetching `size + 1` rows | large table or feed; stable under inserts; same cost at any depth |

A slice is not a different way to find rows. It is an offset page that does not pay for the count.
That is why it shares `OffsetRequest`.

## 4. Keyset in three rules

1. The sort must be total: `ORDER BY created_at, id`, never `created_at` alone. `id` is the tie breaker.
2. The cursor is the sort key values of the last row on the page. SQL:
   `WHERE (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT size + 1`.
3. On the wire the cursor is one opaque string (`Cursor.value`). The client passes it back; it never builds one.

`Cursor.of("2026-10-19T10:00:00Z", "42")` builds one from the key values; `parts()` gets them back.
Turning typed values into strings and back is the caller's job.

Two facts proved against Postgres 17 (see `KeysetSqlProvingGroundTest`):

- Write the predicate as a row value, `(created_at, id) > (?, ?)`. Postgres seeks into the index
  (`Index Cond`). The equivalent `created_at > ? OR (created_at = ? AND id > ?)` walks the index from
  the start and filters, so deep pages get slow again.
- Build the cursor from the values the database returned, not the ones the application created.
  Postgres rounds timestamps to microseconds; a nanosecond `Instant` points at a row that is not there.
  The scroll adapter does this for you: Spring Data hands back the last row's values as loaded.

Spring Data's keyset scrolling writes the OR-chain form (`KeysetJpaProvingGroundTest`). It is correct,
and it composes with a `Specification`; it is just not depth-proof on Postgres. Reach for a native
row-value query only when a real list is deep enough for `EXPLAIN` to show the walk hurting.

## 5. Building a result from a query

```kotlin
val request = policy.keyset(after = rawAfterParam, size = rawSizeParam)   // throws PaginationException on bad input
val rows = query(afterKeys = request.after?.parts(), limit = request.size + 1)
val page = KeysetPage.fromOverFetch(rows, request) { row -> Cursor.of(row.createdAt.toString(), row.id.toString()) }
```

`OffsetPage(items, request, totalElements)` and `Slice.fromOverFetch(rows, request)` follow the same pattern.

## 6. In a Spring MVC service

Import the configuration. Declare a `PaginationPolicy` bean if the default (20 per page, 100 at most) is wrong for you.

```kotlin
@Configuration
@Import(PaginationConfiguration::class)
class WebConfiguration {
    @Bean fun paginationPolicy() = PaginationPolicy(defaultSize = 25, maxSize = 200)
}
```

A controller declares the request type it wants and returns the matching response model.
The resolver reads `page`, `size` and `after` from the query string and runs them through the policy.

```kotlin
@GetMapping("/listings")
fun listings(request: OffsetRequest): OffsetPageResponse<ListingDto> =
    repository.findAll(request.toPageable(Sort.by("createdAt", "id")))
        .toOffsetPage(request)
        .map(ListingDto::from)
        .toResponse()

// written once, next to the list: the sort IS the keyset, and the last key must be unique
private val keys = KeysetKeys(KeysetKey.offsetDateTime("createdAt"), KeysetKey.uuid("id"))

@GetMapping("/feed")
fun feed(request: KeysetRequest): KeysetPageResponse<ListingDto> =
    repository
        .findBy(filter) { q -> q.sortBy(keys.sort()).limit(request.size).scroll(request.toScrollPosition(keys)) }
        .toKeysetPage(request, keys)
        .map(ListingDto::from)
        .toResponse()
```

`filter` is any `Specification` (the Selector library will produce it); Spring Data ANDs the cursor
predicate onto it and writes one query. The cursor says where you are; the filter says which list
you are in; the client must send the same filter with the cursor.

Without Spring Data, build the page from your own query with `KeysetPage.fromOverFetch(rows, request) { row -> Cursor.of(...) }`.

Bad input is a 400 problem detail that names the parameter:

```json
{ "type": "about:blank", "title": "Invalid pagination", "status": 400,
  "detail": "size: must be at most 100, was 101", "parameter": "size" }
```

The response models are flat on purpose. `OffsetPageResponse` has `items`, `page`, `size`,
`totalElements`, `totalPages`, `hasPrevious`, `hasNext`. `SliceResponse` drops the totals.
`KeysetPageResponse` has `items`, `size`, `nextCursor` (a string, null on the last page) and `hasNext`.

## 7. Tests and the proving ground

`pagination-core` tests are plain unit tests. `pagination-spring` holds the MVC tests and the
proving ground: `KeysetSqlProvingGroundTest`, `KeysetJpaProvingGroundTest` and `ScrollAdaptersTest`
run against a real Postgres 17 in Testcontainers, so building that module needs Docker. The same
holds for messaging-spring; one rule for the repo.
