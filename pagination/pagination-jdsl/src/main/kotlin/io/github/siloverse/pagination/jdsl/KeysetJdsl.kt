package io.github.siloverse.pagination.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.expression.Expressionable
import com.linecorp.kotlinjdsl.querymodel.jpql.path.Path
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import com.linecorp.kotlinjdsl.querymodel.jpql.sort.Sortable
import io.github.siloverse.pagination.core.keyset.KeysetKeys
import io.github.siloverse.pagination.core.keyset.KeysetPage
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.sort.Direction
import kotlin.reflect.KProperty1

/*
 * Keyset paging through Kotlin JDSL: the three things JDSL does not have.
 *
 * 1. keysetAfter: the predicate for "rows after the cursor". When every key sorts the same
 *    way it is ONE row-value comparison, `(a, b) > (?, ?)`, which Hibernate renders as a SQL
 *    row value and Postgres answers with an index seek (proven in HqlTupleComparisonTest).
 *    Mixed directions cannot be a row value; they become the OR chain, which is correct but
 *    walks the index.
 * 2. keysetOrder: the ORDER BY, from the same keys, so it cannot drift from the cursor.
 * 3. toKeysetPage: `size + 1` rows in, a page with the next cursor out, the cursor built from
 *    the last row's values as loaded.
 */

/** Null on the first page. */
fun <E : Any> Jpql.keysetAfter(request: KeysetRequest, keys: KeysetKeys, keyset: JdslKeyset<E>): Predicatable? {
    val cursor = request.after ?: return null
    val values = keys.decode(cursor)
    val paths = keys.properties.map { pathOf(keyset.property(it)) }

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
        val equalBefore = (0 until i).map { j -> paths[j].equal(values[j]) }
        val step = ordered<Int>(paths[i], keys.keys[i].direction, values[i])
        and(*(equalBefore + step).toTypedArray())
    }
    return or(*branches.toTypedArray())
}

/** The ORDER BY for these keys, directions included. Pass as `orderBy(*keysetOrder(...).toTypedArray())`. */
fun <E : Any> Jpql.keysetOrder(keys: KeysetKeys, keyset: JdslKeyset<E>): List<Sortable> =
    keys.keys.map { key ->
        val path = pathOf(keyset.property(key.property))
        if (key.direction == Direction.ASC) path.asc() else path.desc()
    }

/** Rows fetched with `limit = request.size + 1`, in key order, become the page. */
fun <E : Any> List<E?>.toKeysetPage(request: KeysetRequest, keys: KeysetKeys, keyset: JdslKeyset<E>): KeysetPage<E> =
    KeysetPage.fromOverFetch(filterNotNull(), request) { row ->
        keys.cursorOf(keys.properties.map { keyset.property(it).get(row) ?: error("sort column '$it' is null on a row; keyset needs non-null keys") })
    }

@Suppress("UNCHECKED_CAST")
private fun <E : Any> Jpql.pathOf(property: KProperty1<E, *>): Path<Any> = path(property as KProperty1<E, Any>)

/** JDSL's ordered comparisons want `T : Comparable<T>`; the type is only known at runtime, so it is erased. */
@Suppress("UNCHECKED_CAST")
private fun <T : Comparable<T>> Jpql.ordered(path: Path<Any>, direction: Direction, value: Any): Predicatable {
    val typed = path as Path<T>
    return if (direction == Direction.ASC) typed.greaterThan(value as T) else typed.lessThan(value as T)
}
