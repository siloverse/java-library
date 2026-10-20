package io.github.siloverse.selector.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import io.github.siloverse.selector.core.definition.Field
import io.github.siloverse.selector.core.definition.FilterDefinition
import io.github.siloverse.selector.core.filter.Condition
import kotlin.reflect.KProperty1

/**
 * A custom predicate for one field: the escape hatch for what a plain path comparison
 * cannot say, such as a subquery or a join. Receives the typed [Condition].
 */
typealias JdslStrategy = Jpql.(Condition<*>) -> Predicatable

/**
 * Ties a [FilterDefinition] to an entity, so each condition can become a JDSL predicate.
 *
 * Every field is bound once, either to an entity property reference (`Listing::price`) or
 * to a [JdslStrategy]. The compiler checks that a `Field<Long>` binds to a `Long` property.
 * The builder checks that every field is bound and none twice, so a filter that cannot be
 * executed fails when the list is written, not when a client calls it.
 *
 * ```
 * val bindings = JdslBindings<Listing>(definition) {
 *     bind(price, Listing::price)
 *     strategy(search) { condition -> path(Listing::title).like("%${condition.value}%") }
 * }
 * ```
 */
class JdslBindings<E : Any> private constructor(
    val definition: FilterDefinition,
    private val properties: Map<String, KProperty1<E, *>>,
    private val strategies: Map<String, JdslStrategy>,
) {

    fun strategy(fieldName: String): JdslStrategy? = strategies[fieldName]

    fun property(fieldName: String): KProperty1<E, *> =
        properties[fieldName] ?: error("field '$fieldName' is bound by a strategy, not a property")

    class Builder<E : Any>(private val definition: FilterDefinition) {
        private val properties = mutableMapOf<String, KProperty1<E, *>>()
        private val strategies = mutableMapOf<String, JdslStrategy>()

        /** A plain comparison on an entity property. The property's type must match the field's type. */
        fun <T : Any> bind(field: Field<T>, property: KProperty1<E, T?>) {
            claim(field)
            properties[field.name] = property
        }

        /** Anything a plain comparison cannot say. */
        fun strategy(field: Field<*>, strategy: JdslStrategy) {
            claim(field)
            strategies[field.name] = strategy
        }

        private fun claim(field: Field<*>) {
            require(definition.field(field.name) === field) { "field '${field.name}' is not part of this definition" }
            require(field.name !in properties && field.name !in strategies) { "field '${field.name}' is bound twice" }
        }

        fun build(): JdslBindings<E> {
            val unbound = definition.fieldNames - properties.keys - strategies.keys
            require(unbound.isEmpty()) { "fields with no binding: $unbound; bind a property or give a strategy" }
            return JdslBindings(definition, properties.toMap(), strategies.toMap())
        }
    }

    companion object {
        operator fun <E : Any> invoke(definition: FilterDefinition, bind: Builder<E>.() -> Unit): JdslBindings<E> =
            Builder<E>(definition).apply(bind).build()
    }
}
