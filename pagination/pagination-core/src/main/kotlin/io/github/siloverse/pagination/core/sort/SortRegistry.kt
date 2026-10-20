package io.github.siloverse.pagination.core.sort

import kotlin.reflect.KClass

/**
 * Every [SortContract] of the application, by entity. One per entity; two is a startup error.
 * An entity with no contract cannot be sorted by the client at all.
 */
class SortRegistry(contracts: Collection<SortContract>) {

    private val byEntity: Map<KClass<*>, SortContract> = contracts
        .groupBy { it.entityType }
        .mapValues { (entity, found) ->
            require(found.size == 1) { "${found.size} sort contracts for ${entity.simpleName}: ${found.map { it::class.simpleName }}" }
            found.single()
        }

    fun sortableFor(entity: KClass<*>): SortableFields? = byEntity[entity]?.sortable

    val entities: Set<KClass<*>>
        get() = byEntity.keys
}
