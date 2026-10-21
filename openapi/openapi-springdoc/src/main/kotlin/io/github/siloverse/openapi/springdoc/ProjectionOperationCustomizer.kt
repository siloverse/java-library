package io.github.siloverse.openapi.springdoc

import io.github.siloverse.pagination.spring.response.KeysetPageResponse
import io.github.siloverse.pagination.spring.response.OffsetPageResponse
import io.github.siloverse.pagination.spring.response.SliceResponse
import io.github.siloverse.projector.core.Projectable
import io.github.siloverse.projector.core.Projection
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.http.ResponseEntity
import org.springframework.web.method.HandlerMethod
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Documents the optional parts an endpoint can include: for a [Projection] parameter, `include`
 * and `exclude` as repeatable parameters whose values are an enum of the returned DTO's
 * `@Projectable` names. The client generator turns the enum into a Kotlin enum, so a wrong
 * name is a compile error for the consumer.
 *
 * The DTO is found in the return type, unwrapped from `ResponseEntity`, the pagination
 * response models and collections.
 */
class ProjectionOperationCustomizer : OperationCustomizer {

    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        if (handlerMethod.methodParameters.none { it.parameterType == Projection::class.java }) return operation
        val dto = dtoOf(handlerMethod.returnType.genericParameterType) ?: return operation
        val projectables = projectablesOf(dto)
        if (projectables.isEmpty()) return operation

        val names = projectables.map { it.name }
        val defaults = projectables.filter { it.isDefault }.map { it.name }
        operation.addParametersItem(
            repeatableQueryParameter("include", "Optional parts to include. On by default: $defaults", StringSchema()._enum(names)),
        )
        operation.addParametersItem(
            repeatableQueryParameter("exclude", "Default parts to leave out", StringSchema()._enum(names)),
        )
        return operation
    }

    private fun dtoOf(type: Type): Class<*>? {
        if (type is Class<*>) return type
        if (type !is ParameterizedType) return null
        val raw = type.rawType as? Class<*> ?: return null
        val wrappers = listOf(ResponseEntity::class.java, KeysetPageResponse::class.java, OffsetPageResponse::class.java, SliceResponse::class.java)
        return when {
            wrappers.any { it == raw } || Collection::class.java.isAssignableFrom(raw) -> dtoOf(type.actualTypeArguments[0])
            Map::class.java.isAssignableFrom(raw) -> dtoOf(type.actualTypeArguments[1])
            else -> raw
        }
    }

    private fun projectablesOf(dto: Class<*>): List<Projectable> =
        generateSequence(dto) { it.superclass }.takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .mapNotNull { it.getAnnotation(Projectable::class.java) }
            .toList()
}
