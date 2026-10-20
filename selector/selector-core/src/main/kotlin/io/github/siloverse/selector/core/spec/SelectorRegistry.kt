package io.github.siloverse.selector.core.spec

import io.github.siloverse.selector.core.error.SelectorException
import io.github.siloverse.selector.core.selector.Selector
import kotlin.reflect.KClass

/**
 * Every [SelectorSpec] of the application, by entity and key. Two specs for one key on one
 * entity is a startup error. A selector with no spec is a client error that lists what the
 * entity offers: a typo must never silently return the whole table.
 */
class SelectorRegistry(specs: Collection<SelectorSpec<*>>) {

    private data class Key(val entityType: KClass<*>, val key: String)

    private val byKey: Map<Key, SelectorSpec<*>> = specs
        .groupBy { Key(it.entityType, it.key) }
        .mapValues { (key, found) ->
            require(found.size == 1) { "${found.size} specs for ${key.entityType.simpleName} ${key.key}: ${found.map { it::class.simpleName }}" }
            found.single()
        }

    fun specFor(selector: Selector): SelectorSpec<*> =
        byKey[Key(selector.entityType, selector.key)]
            ?: throw SelectorException(selector.key, "unknown filter; filters for ${selector.entityType.simpleName}: ${keysFor(selector.entityType)}")

    fun keysFor(entityType: KClass<*>): List<String> =
        byKey.keys.filter { it.entityType == entityType }.map { it.key }.sorted()

    val specs: Collection<SelectorSpec<*>>
        get() = byKey.values
}
