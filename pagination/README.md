# siloverse pagination

Request and result types for paging through a list, plus the policy that turns raw client
input into a request the service can trust. Offset, slice and keyset, in one shape.

**The rule, in one line:** the policy is the only door from client input to a request;
it rejects bad input, it never clamps it.

## Modules

| module | depends on | contents |
|---|---|---|
| `pagination-core` | nothing | `OffsetRequest` / `OffsetPage`, `Slice`, `KeysetRequest` / `KeysetPage`, `Cursor`, `PaginationPolicy`, `PaginationException` |
| `pagination-bom` | — | version alignment for consumers |

`pagination-spring` (query-param argument resolver, `Pageable` adapters, 400 mapping) is next.

## The three kinds of page

| kind | request | result | counts? | knows next page by | use when |
|---|---|---|---|---|---|
| offset | `OffsetRequest(page, size)` | `OffsetPage` | yes (`COUNT`) | total | small table, user wants page numbers |
| slice | `OffsetRequest(page, size)` | `Slice` | no | fetching `size + 1` rows | offset is fine but `COUNT` is too expensive |
| keyset | `KeysetRequest(after, size)` | `KeysetPage` | no | fetching `size + 1` rows | large table or feed; stable under inserts; same cost at any depth |

A slice is not a different way to find rows. It is an offset page that does not pay for the count.
That is why it shares `OffsetRequest`.

## Keyset in three rules

1. The sort must be total: `ORDER BY created_at, id`, never `created_at` alone. `id` is the tie breaker.
2. The cursor is the sort key values of the last row on the page. SQL:
   `WHERE (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT size + 1`.
3. On the wire the cursor is one opaque string (`Cursor.value`). The client passes it back; it never builds one.

`Cursor.of("2026-10-19T10:00:00Z", "42")` builds one from the key values; `parts()` gets them back.
Turning typed values into strings and back is the caller's job.

## Building a result from a query

```kotlin
val request = policy.keyset(after = rawAfterParam, size = rawSizeParam)   // throws PaginationException on bad input
val rows = query(afterKeys = request.after?.parts(), limit = request.size + 1)
val page = KeysetPage.fromOverFetch(rows, request) { row -> Cursor.of(row.createdAt.toString(), row.id.toString()) }
```

`OffsetPage(items, request, totalElements)` and `Slice.fromOverFetch(rows, request)` follow the same pattern.
