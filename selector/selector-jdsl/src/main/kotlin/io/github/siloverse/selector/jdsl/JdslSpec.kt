package io.github.siloverse.selector.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import io.github.siloverse.selector.core.spec.FieldType
import io.github.siloverse.selector.core.spec.SelectorSpec
import kotlin.reflect.KClass

/**
 * A filter that becomes a Kotlin JDSL predicate. Declare one bean per filter; the compiler
 * gives [predicate] the typed values, the `Jpql` receiver gives it `path`, `and`, subqueries,
 * whatever the filter needs. Repositories may be injected for lookups.
 *
 * ```
 * @Component
 * class OrderCheckedOutAfterSpec : JdslSingleValueSpec<OffsetDateTime>(
 *     Order::class, "order", "checked-out-at", "after", FieldType.OFFSET_DATE_TIME,
 * ) {
 *     override fun Jpql.predicate(value: OffsetDateTime): Predicatable =
 *         path(Order::originOrder).path(OriginOrder::createdAt).greaterThan(value)
 * }
 * ```
 */
abstract class JdslSpec<T : Any>(
    entityType: KClass<*>,
    namespace: String,
    attribute: String,
    constraint: String? = null,
    type: FieldType<T>,
    arity: Arity,
) : SelectorSpec<T>(entityType, namespace, attribute, constraint, type, arity) {

    abstract fun Jpql.predicate(values: List<T>): Predicatable
}

abstract class JdslSingleValueSpec<T : Any>(
    entityType: KClass<*>,
    namespace: String,
    attribute: String,
    constraint: String? = null,
    type: FieldType<T>,
) : JdslSpec<T>(entityType, namespace, attribute, constraint, type, Arity.SINGLE) {

    abstract fun Jpql.predicate(value: T): Predicatable

    final override fun Jpql.predicate(values: List<T>): Predicatable = predicate(values.single())
}

abstract class JdslMultiValueSpec<T : Any>(
    entityType: KClass<*>,
    namespace: String,
    attribute: String,
    constraint: String? = null,
    type: FieldType<T>,
) : JdslSpec<T>(entityType, namespace, attribute, constraint, type, Arity.MULTI)
