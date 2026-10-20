package io.github.siloverse.pagination.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.expression.Expressionable
import com.linecorp.kotlinjdsl.querymodel.jpql.path.Path
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import com.linecorp.kotlinjdsl.querymodel.jpql.sort.Sortable
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.sort.Direction
import io.github.siloverse.pagination.core.sort.SortRequest

/*
 * Paging through Kotlin JDSL: the things JDSL does not have, built from the request's sort
 * and the entity's SortSpec.
 *
 * keysetAfter: the predicate for "rows after the cursor". When every key sorts the same way it
 * is ONE row-value comparison, `(a, b) > (?, ?)`, which Hibernate renders as a SQL row value and
 * Postgres answers with an index seek (proven in HqlTupleComparisonTest). Mixed directions
 * cannot be a row value; they become the OR chain, correct but an index walk.
 * sortOrder / keysetOrder: the ORDER BY from the same sort, so it cannot drift from the cursor.
 * toKeysetPage: `size + 1` rows in, a page with the next cursor out, built from the last row.
 */

/** Null on the first page. */
fun <E : Any> Jpql.keysetAfter(request: KeysetRequest, spec: SortSpec<E>): Predicatable? {
    val cursor = request.after ?: return null
    val keys = spec.sortable.keysFor(request.sort)
    val values = keys.decode(cursor)
    val paths = keys.properties.map { spec.binding(it).path(this) }

    if (keys.sameDirection) {
        val n = keys.keys.size
        val left = (0 until n).joinToString(", ", "(", ")") { "{$it}" }
        val right = (n until 2 * n).joinToString(", ", "(", ")") { "{$it}" }
        val op = if (keys.direction == Direction.ASC) ">" else "<"
        val args: List<Expressionable<*>> = paths + values.map { value(it) }
        return customPredicate("$left $op $right", *args.toTypedArray())
    }

    // a > ?  OR  (a = ? AND b > ?)  OR  (a = ? AND b = ? AND c > ?) ...; ">" or "<" per key
    val branches = keys.keys.indices.map { i ->
        val equalBefore = (0 until i).map { j -> anyPath(paths[j]).equal(values[j]) }
        val step = ordered<Int>(anyPath(paths[i]), keys.keys[i].direction, values[i])
        and(*(equalBefore + step).toTypedArray())
    }
    return or(*branches.toTypedArray())
}

/** The ORDER BY for a validated sort. Pass as `orderBy(*sortOrder(...).toTypedArray())`. */
fun <E : Any> Jpql.sortOrder(sort: SortRequest, spec: SortSpec<E>): List<Sortable> =
    sort.orders.map { order ->
        val path = anyPath(spec.binding(order.field).path(this))
        if (order.direction == Direction.ASC) path.asc() else path.desc()
    }

fun <E : Any> Jpql.keysetOrder(request: KeysetRequest, spec: SortSpec<E>): List<Sortable> = sortOrder(request.sort, spec)

/** The ORDER BY for an offset request, or none when the list has a fixed order. */
fun <E : Any> Jpql.sortOrder(request: OffsetRequest, spec: SortSpec<E>): List<Sortable> =
    request.sort?.let { sortOrder(it, spec) } ?: emptyList()

/** Rows fetched with `limit = request.size + 1`, in sort order, become the page. */
fun <E : Any> List<E?>.toKeysetPage(request: KeysetRequest, spec: SortSpec<E>): KeysetPage<E> {
    val keys = spec.sortable.keysFor(request.sort)
    return KeysetPage.fromOverFetch(filterNotNull(), request) { row ->
        keys.cursorOf(
            keys.properties.map { spec.binding(it).value(row) ?: error("sort column '$it' is null on a row; keyset needs non-null keys") },
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun anyPath(path: Path<*>): Path<Any> = path as Path<Any>

/** JDSL's ordered comparisons want `T : Comparable<T>`; the type is only known at runtime, so it is erased. */
@Suppress("UNCHECKED_CAST")
private fun <T : Comparable<T>> Jpql.ordered(path: Path<Any>, direction: Direction, value: Any): Predicatable {
    val typed = path as Path<T>
    return if (direction == Direction.ASC) typed.greaterThan(value as T) else typed.lessThan(value as T)
}
