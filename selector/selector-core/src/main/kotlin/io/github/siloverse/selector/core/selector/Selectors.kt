package io.github.siloverse.selector.core.selector

import io.github.siloverse.selector.core.error.SelectorException
import kotlin.reflect.KClass

/**
 * Turns a query string into [Selector]s. Only keys that start with `filter.` are filters;
 * paging and sorting keys are simply not looked at. The grammar is
 * `filter.<namespace>.<attribute>` or `filter.<namespace>.<attribute>.<constraint>`, nothing
 * else: a malformed filter key is a client error, never ignored.
 */
object Selectors {

    const val PREFIX = "filter"

    fun key(namespace: String, attribute: String, constraint: String?): String =
        listOfNotNull(PREFIX, namespace, attribute, constraint).joinToString(".")

    fun parse(entityType: KClass<*>, params: Map<String, List<String>>): List<Selector> =
        params.entries
            .filter { it.key == PREFIX || it.key.startsWith("$PREFIX.") }
            .map { (key, values) -> parseOne(entityType, key, values) }

    private fun parseOne(entityType: KClass<*>, key: String, values: List<String>): Selector {
        val parts = key.split(".")
        if (parts.size !in 3..4 || parts.any { it.isBlank() }) {
            throw SelectorException(key, "is not '$PREFIX.<namespace>.<attribute>' or '$PREFIX.<namespace>.<attribute>.<constraint>'")
        }
        return Selector(entityType, parts[1], parts[2], parts.getOrNull(3), values)
    }
}
