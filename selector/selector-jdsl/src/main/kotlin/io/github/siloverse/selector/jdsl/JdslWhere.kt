package io.github.siloverse.selector.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.path.Path
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import io.github.siloverse.selector.core.definition.Operator
import io.github.siloverse.selector.core.filter.Condition
import io.github.siloverse.selector.core.filter.Selection
import kotlin.reflect.KProperty1

/**
 * The WHERE clause: every condition of the [Selection] as a JDSL predicate, ANDed.
 * Null when the selection is empty, so the caller can pass it straight to `where(...)`.
 *
 * Values travel as query parameters, never as text in the JPQL. A `like` pattern is passed
 * through as the client sent it; the client controls the `%`.
 */
fun <E : Any> Jpql.where(selection: Selection, bindings: JdslBindings<E>): Predicatable? {
    if (selection.isEmpty) return null
    val predicates = selection.conditions.map { predicate(it, bindings) }
    return and(*predicates.toTypedArray())
}

private fun <E : Any> Jpql.predicate(condition: Condition<*>, bindings: JdslBindings<E>): Predicatable {
    val strategy = bindings.strategy(condition.field.name)
    if (strategy != null) return strategy(condition)

    @Suppress("UNCHECKED_CAST")
    val property = bindings.property(condition.field.name) as KProperty1<E, Any>
    val path = path(property)

    return when (condition.operator) {
        Operator.EQ -> path.equal(condition.value)
        Operator.NE -> path.notEqual(condition.value)
        Operator.IN -> path.`in`(*condition.values.toTypedArray())
        Operator.LIKE -> likePath(path).like(condition.value as String)
        Operator.LT, Operator.LTE, Operator.GT, Operator.GTE -> ordered<Int>(path, condition.operator, condition.value)
    }
}

/**
 * JDSL's ordered comparisons are declared on `T : Comparable<T>`. The condition's value type is
 * only known at runtime (the field's [io.github.siloverse.selector.core.definition.FieldType]
 * chose it), so the generic is satisfied with a placeholder type and erased: JDSL stores the
 * value as a parameter and never looks at the type parameter.
 */
@Suppress("UNCHECKED_CAST")
private fun <T : Comparable<T>> Jpql.ordered(path: Path<Any>, operator: Operator, value: Any): Predicatable {
    val typed = path as Path<T>
    val v = value as T
    return when (operator) {
        Operator.LT -> typed.lessThan(v)
        Operator.LTE -> typed.lessThanOrEqualTo(v)
        Operator.GT -> typed.greaterThan(v)
        Operator.GTE -> typed.greaterThanOrEqualTo(v)
        else -> error("not an ordered operator: $operator")
    }
}

@Suppress("UNCHECKED_CAST")
private fun likePath(path: Path<Any>): Path<String> = path as Path<String>
