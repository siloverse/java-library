package io.github.siloverse.pagination.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.path.Path
import io.github.siloverse.pagination.core.keyset.KeysetKey
import io.github.siloverse.pagination.core.sort.SortContract
import io.github.siloverse.pagination.core.sort.SortOrder
import io.github.siloverse.pagination.core.sort.SortableFields
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * The order contract of one entity, declared once as a bean: which fields a client may sort
 * by, how each value travels through the cursor, how to reach the column, the tie breaker
 * and the default order. The web layer validates `sort=` against it; the keyset functions
 * build the ORDER BY, the after-cursor predicate and the next cursor from it.
 *
 * ```
 * @Component
 * class OrderSort : SortSpec<Order>(Order::class, tieBreaker = "uuid") {
 *     val createdAt = sortable(KeysetKey.offsetDateTime("createdAt"), Order::createdAt)
 *     val uuid = sortable(KeysetKey.uuid("uuid"), Order::uuid)
 *     val checkedOutAt = sortable(KeysetKey.offsetDateTime("checkedOutAt"),
 *         path = { path(Order::originOrder).path(OriginOrder::createdAt) }, value = { it.originOrder.createdAt })
 * }
 * ```
 */
abstract class SortSpec<E : Any>(
    final override val entityType: KClass<E>,
    private val tieBreaker: String,
    private val defaultSort: List<SortOrder> = emptyList(),
) : SortContract {

    internal class Binding<E : Any>(val key: KeysetKey<*>, val path: Jpql.() -> Path<*>, val value: (E) -> Any?)

    private val bindings = linkedMapOf<String, Binding<E>>()

    /** A column on the entity itself. The compiler checks the key's type against the property's. */
    protected fun <T : Any> sortable(key: KeysetKey<T>, property: KProperty1<E, T?>): KeysetKey<T> =
        register(key, { @Suppress("UNCHECKED_CAST") path(property as KProperty1<E, Any>) }, { property.get(it) })

    /** A column reached through a path, with how to read its value from a loaded row. */
    protected fun <T : Any> sortable(key: KeysetKey<T>, path: Jpql.() -> Path<T>, value: (E) -> T?): KeysetKey<T> =
        register(key, path, value)

    private fun <T : Any> register(key: KeysetKey<T>, path: Jpql.() -> Path<*>, value: (E) -> Any?): KeysetKey<T> {
        require(key.property !in bindings) { "'${key.property}' is declared twice on ${this::class.simpleName}" }
        bindings[key.property] = Binding(key, path, value)
        return key
    }

    /** Validated on first use, after the subclass has declared its fields. The registry reads it at startup. */
    final override val sortable: SortableFields by lazy {
        SortableFields(bindings.values.map { it.key }, tieBreaker, defaultSort)
    }

    internal fun binding(name: String): Binding<E> =
        bindings[name] ?: throw IllegalArgumentException("'$name' is not a sortable field of ${entityType.simpleName}")
}
