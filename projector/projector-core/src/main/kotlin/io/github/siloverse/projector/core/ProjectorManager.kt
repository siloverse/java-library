package io.github.siloverse.projector.core

import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Fills the optional parts of a page of DTOs: one batched projector call per active part.
 *
 * Give it the entities and their DTOs in order. Mutable fields are written in place; a DTO
 * whose projectable fields are `val`s is rebuilt through its primary constructor and the new
 * instance returned. Unknown names in the projection are a client error naming the parameter.
 *
 * A projector that returns DTOs which project back (item → order → item) is bounded by the
 * projector's `maxDepth`: beyond it the field is left untouched instead of recursing forever.
 */
class ProjectorManager(private val registry: ProjectorRegistry) {

    private val depths = ThreadLocal.withInitial { mutableMapOf<Projector<*, *>, Int>() }

    fun <T : Any, D : Any> apply(entityType: KClass<T>, entities: List<T>, dtos: List<D>, projection: Projection): List<D> {
        require(entities.size == dtos.size) { "${entities.size} entities but ${dtos.size} dtos" }
        if (dtos.isEmpty()) return dtos
        val dtoType = dtos.first()::class
        val plan = registry.plan(entityType, dtoType)
        validate(projection, dtoType)

        val changes = dtos.associateWith { mutableMapOf<ProjectorRegistry.Binding, Any?>() }.toMutableMap()
        for (binding in plan.filter { projection.isActive(it.projectable) }) {
            val projected = descend(binding.projector, entities) ?: continue
            for ((entity, dto) in entities.zip(dtos)) {
                if (entity in projected) changes.getValue(dto)[binding] = projected[entity]
            }
        }
        return dtos.map { dto -> write(dto, changes.getValue(dto)) }
    }

    fun <T : Any, D : Any> apply(entityType: KClass<T>, entity: T, dto: D, projection: Projection): D =
        apply(entityType, listOf(entity), listOf(dto), projection).single()

    private fun validate(projection: Projection, dtoType: KClass<*>) {
        val known = registry.namesOf(dtoType)
        (projection.include - known.toSet()).firstOrNull()?.let { throw ProjectorException(Projection.INCLUDE, "unknown projection '$it'; projections of ${dtoType.simpleName}: $known") }
        (projection.exclude - known.toSet()).firstOrNull()?.let { throw ProjectorException(Projection.EXCLUDE, "unknown projection '$it'; projections of ${dtoType.simpleName}: $known") }
    }

    /** Runs the projector unless this thread is already [Projector.maxDepth] levels deep inside it. */
    private fun descend(projector: Projector<Any, Any?>, entities: List<Any>): Map<Any, Any?>? {
        val active = depths.get()
        val depth = (active[projector] ?: 0) + 1
        if (depth > projector.maxDepth) return null
        active[projector] = depth
        try {
            return projector.project(entities)
        } finally {
            if (depth == 1) active.remove(projector) else active[projector] = depth - 1
        }
    }

    private fun <D : Any> write(dto: D, changes: Map<ProjectorRegistry.Binding, Any?>): D {
        if (changes.isEmpty()) return dto
        val mutable = changes.keys.all { !Modifier.isFinal(it.field.modifiers) }
        if (mutable) {
            changes.forEach { (binding, value) -> binding.field.set(dto, value) }
            return dto
        }
        return rebuild(dto, changes.entries.associate { (binding, value) -> binding.field.name to value })
    }

    /** Immutable DTO: a new instance through the primary constructor, every other parameter copied. */
    @Suppress("UNCHECKED_CAST")
    private fun <D : Any> rebuild(dto: D, overrides: Map<String, Any?>): D {
        val type = dto::class
        val constructor = type.primaryConstructor
            ?: throw IllegalStateException("${type.simpleName} has final @Projectable fields but no primary constructor to rebuild it with")
        val properties = type.memberProperties.associateBy { it.name }
        val arguments = constructor.parameters.associateWith { parameter ->
            val name = parameter.name ?: error("${type.simpleName}'s constructor has an unnamed parameter")
            if (name in overrides) {
                overrides[name]
            } else {
                (properties[name] ?: error("${type.simpleName}.$name is a constructor parameter but not a property")).getter.call(dto)
            }
        }
        return constructor.callBy(arguments) as D
    }
}
