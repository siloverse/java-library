package io.github.siloverse.selector.jdsl

import com.linecorp.kotlinjdsl.dsl.jpql.Jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import io.github.siloverse.selector.core.selector.Selector
import io.github.siloverse.selector.core.spec.SelectorRegistry

/**
 * The one call in a service: [apply] turns the controller's selectors into the WHERE clause.
 *
 * Each selector is matched to its spec by entity and key, its values are parsed by the spec,
 * and the predicates are ANDed. Unknown key, wrong value count or unreadable value are a
 * [io.github.siloverse.selector.core.error.SelectorException] naming the query key. No
 * selectors means no predicate (null), which JDSL's `where` treats as no WHERE clause.
 *
 * Give it every `JdslSpec` bean: `@Bean fun selectorSpecManager(specs: List<JdslSpec<*>>) = SelectorSpecManager(specs)`.
 */
class SelectorSpecManager(specs: Collection<JdslSpec<*>>) {

    val registry = SelectorRegistry(specs)
    private val jpql = Jpql.newInstance()

    fun apply(selectors: List<Selector>): Predicatable? {
        if (selectors.isEmpty()) return null
        val predicates = selectors.map { predicate(it) }
        return jpql.and(*predicates.toTypedArray())
    }

    @Suppress("UNCHECKED_CAST")
    private fun predicate(selector: Selector): Predicatable {
        val spec = registry.specFor(selector) as JdslSpec<Any>
        val values = spec.parse(selector)
        return with(spec) { jpql.predicate(values) }
    }
}
