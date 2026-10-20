package io.github.siloverse.pagination.jdsl

import io.github.siloverse.pagination.core.keyset.KeysetKey
import io.github.siloverse.pagination.core.sort.SortableFields
import kotlin.reflect.KProperty1

/**
 * Ties a list's [SortableFields] to an entity, so the keyset can be turned into an ORDER BY,
 * an "after the cursor" predicate, and the cursor of the last row.
 *
 * Every sortable field is bound once to an entity property reference (`Listing::price`).
 * The compiler checks that a `KeysetKey<Long>` binds to a `Long` property; the builder checks
 * that every field is bound. A list that cannot be ordered fails when it is written, not when
 * a client calls it.
 *
 * ```
 * val keyset = JdslKeyset<Listing>(sortable) {
 *     bind(createdAt, Listing::createdAt)
 *     bind(price, Listing::price)
 *     bind(id, Listing::id)
 * }
 * ```
 */
class JdslKeyset<E : Any> private constructor(
    val sortable: SortableFields,
    private val properties: Map<String, KProperty1<E, *>>,
) {

    fun property(name: String): KProperty1<E, *> =
        properties[name] ?: throw IllegalArgumentException("'$name' is not a sortable field of this list")

    class Builder<E : Any>(private val sortable: SortableFields) {
        private val properties = mutableMapOf<String, KProperty1<E, *>>()

        fun <T : Any> bind(key: KeysetKey<T>, property: KProperty1<E, T?>) {
            require(sortable.isSortable(key.property)) { "'${key.property}' is not a sortable field of this list" }
            require(key.property !in properties) { "'${key.property}' is bound twice" }
            properties[key.property] = property
        }

        fun build(): JdslKeyset<E> {
            val unbound = sortable.fields - properties.keys
            require(unbound.isEmpty()) { "sortable fields with no binding: $unbound" }
            return JdslKeyset(sortable, properties.toMap())
        }
    }

    companion object {
        operator fun <E : Any> invoke(sortable: SortableFields, bind: Builder<E>.() -> Unit): JdslKeyset<E> =
            Builder<E>(sortable).apply(bind).build()
    }
}
