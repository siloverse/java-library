package io.github.siloverse.projector.core

import kotlin.reflect.KClass

/**
 * Computes one optional part of a DTO for a whole page of entities at once.
 *
 * Batching is the point: a page of 25 orders must cost ONE extra query for their items, not
 * 25. [project] receives every entity of the page and returns the value per entity. An entity
 * missing from the map keeps its field untouched.
 *
 * Declared once per part as a bean, matched to the DTO field by [entityType] and [name].
 * Repositories may be injected. [maxDepth] bounds recursion when a projected DTO projects back.
 */
abstract class Projector<T : Any, P>(
    val entityType: KClass<T>,
    val name: String,
    val maxDepth: Int = 1,
) {

    init {
        require(name.isNotBlank() && name.none { it == ',' || it.isWhitespace() }) { "projector name '$name' may not be blank or contain ',' or spaces" }
        require(maxDepth >= 1) { "maxDepth must be 1 or more, was $maxDepth" }
    }

    abstract fun project(entities: List<T>): Map<T, P>

    override fun toString(): String = "${entityType.simpleName}.$name"
}

/** For parts that are cheap per entity, or already loaded: one call per entity, no batching needed. */
abstract class SimpleProjector<T : Any, P>(
    entityType: KClass<T>,
    name: String,
    maxDepth: Int = 1,
) : Projector<T, P>(entityType, name, maxDepth) {

    abstract fun project(entity: T): P

    final override fun project(entities: List<T>): Map<T, P> = entities.associateWith { project(it) }
}
