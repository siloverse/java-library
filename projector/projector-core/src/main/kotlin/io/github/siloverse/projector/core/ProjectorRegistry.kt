package io.github.siloverse.projector.core

import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Every [Projector] of the application, by entity and name, and for each DTO class the plan
 * of which projector feeds which field.
 *
 * Two projectors for one name on one entity is a startup error. A DTO field with no projector,
 * or a projector whose output does not fit the field, is an error the first time that DTO is
 * projected, naming the field and the fix. Plans are computed once per entity and DTO pair.
 */
class ProjectorRegistry(projectors: Collection<Projector<*, *>>) {

    private data class Key(val entityType: KClass<*>, val name: String)

    /** One DTO field and the projector that feeds it. */
    class Binding internal constructor(val projectable: Projectable, val field: Field, val projector: Projector<Any, Any?>)

    private val byKey: Map<Key, Projector<*, *>> = projectors
        .groupBy { Key(it.entityType, it.name) }
        .mapValues { (key, found) ->
            require(found.size == 1) { "${found.size} projectors for ${key.entityType.simpleName}.${key.name}: ${found.map { it::class.simpleName }}" }
            found.single()
        }

    private val plans = ConcurrentHashMap<Pair<KClass<*>, KClass<*>>, List<Binding>>()

    /** The bindings of [dtoType] for entities of [entityType], in field order; computed once. */
    fun plan(entityType: KClass<*>, dtoType: KClass<*>): List<Binding> =
        plans.computeIfAbsent(entityType to dtoType) { computePlan(entityType, dtoType) }

    fun namesOf(dtoType: KClass<*>): List<String> = projectableFields(dtoType.java).map { it.getAnnotation(Projectable::class.java).name }

    @Suppress("UNCHECKED_CAST")
    private fun computePlan(entityType: KClass<*>, dtoType: KClass<*>): List<Binding> =
        projectableFields(dtoType.java).map { field ->
            val projectable = field.getAnnotation(Projectable::class.java)
            val projector = byKey[Key(entityType, projectable.name)]
                ?: throw IllegalStateException(
                    "${dtoType.simpleName}.${field.name} is @Projectable(\"${projectable.name}\") but no Projector<${entityType.simpleName}, …> named '${projectable.name}' exists",
                )
            checkFits(projector, field, dtoType)
            field.isAccessible = true
            Binding(projectable, field, projector as Projector<Any, Any?>)
        }

    private fun projectableFields(dto: Class<*>): List<Field> =
        generateSequence(dto) { it.superclass }.takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { it.isAnnotationPresent(Projectable::class.java) }
            .toList()
            .also { fields ->
                val twice = fields.groupBy { it.getAnnotation(Projectable::class.java).name }.filterValues { it.size > 1 }.keys
                require(twice.isEmpty()) { "${dto.simpleName} declares @Projectable names twice: $twice" }
            }

    /** The projector's output type must be the field's type; checked when the generics can be resolved. */
    private fun checkFits(projector: Projector<*, *>, field: Field, dtoType: KClass<*>) {
        val output = outputTypeOf(projector::class.java) ?: return
        val fits = sameType(output, field.genericType) || (output is Class<*> && field.type.isAssignableFrom(output))
        check(fits) {
            "${projector::class.simpleName ?: projector::class.java.name} produces ${output.typeName} but ${dtoType.simpleName}.${field.name} is ${field.genericType.typeName}"
        }
    }

    /**
     * Structural equality that ignores wildcards: Kotlin writes `List<ItemDto>` in a supertype
     * position as `List<? extends ItemDto>` (List is covariant), while a field keeps `List<ItemDto>`.
     */
    private fun sameType(a: Type, b: Type): Boolean {
        val x = unwrap(a)
        val y = unwrap(b)
        if (x is ParameterizedType && y is ParameterizedType) {
            return x.rawType == y.rawType && x.actualTypeArguments.size == y.actualTypeArguments.size &&
                x.actualTypeArguments.zip(y.actualTypeArguments).all { (p, q) -> sameType(p, q) }
        }
        return x == y
    }

    private fun unwrap(type: Type): Type = if (type is WildcardType) unwrap(type.upperBounds.firstOrNull() ?: Any::class.java) else type

    /** Walks the superclass chain to [Projector], substituting type variables, to find P. */
    private fun outputTypeOf(projectorClass: Class<*>): Type? {
        var bindings: Map<TypeVariable<*>, Type> = emptyMap()
        var current: Class<*> = projectorClass
        while (current != Projector::class.java) {
            val generic = current.genericSuperclass ?: return null
            val raw = (generic as? ParameterizedType)?.rawType as? Class<*> ?: generic as? Class<*> ?: return null
            if (generic is ParameterizedType) {
                bindings = raw.typeParameters.zip(generic.actualTypeArguments).associate { (variable, argument) ->
                    variable to (bindings[argument] ?: argument)
                }
            }
            current = raw
        }
        val output = bindings[Projector::class.java.typeParameters[1]] ?: return null
        return output.takeUnless { it is TypeVariable<*> }
    }
}
