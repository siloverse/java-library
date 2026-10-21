package io.github.siloverse.openapi.springdoc

import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.core.sort.SortRegistry
import io.github.siloverse.pagination.core.sort.SortableFields
import io.github.siloverse.pagination.spring.web.Paged
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.StringSchema
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.core.MethodParameter
import org.springframework.web.method.HandlerMethod
import java.math.BigDecimal
import kotlin.reflect.KClass

/**
 * Documents paging and sorting: `page`/`size` for an [OffsetRequest], `after`/`size` for a
 * [KeysetRequest], and `sort` when the entity has a sort contract, with the sortable fields,
 * the default order and the tie breaker in the description. Limits come from the policy.
 *
 * The entity is found the way the resolver finds it: `@Paged` on the parameter, else any
 * sibling annotation with an `entity` attribute such as `@Selectors`.
 */
class PagingOperationCustomizer(
    private val policy: PaginationPolicy,
    private val sorts: SortRegistry,
) : OperationCustomizer {

    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        for (parameter in handlerMethod.methodParameters) {
            val sortable = entityOf(parameter)?.let { sorts.sortableFor(it) }
            when (parameter.parameterType) {
                OffsetRequest::class.java -> {
                    operation.addParametersItem(queryParameter("page", "Page number, counted from 0", IntegerSchema().minimum(BigDecimal.ZERO)._default(0)))
                    operation.addParametersItem(sizeParameter())
                    sortable?.let { operation.addParametersItem(sortParameter(it)) }
                }
                KeysetRequest::class.java -> {
                    operation.addParametersItem(queryParameter("after", "Cursor from `nextCursor` of the previous page. Omit for the first page. Opaque: pass it back unchanged, with the same sort and filters.", StringSchema()))
                    operation.addParametersItem(sizeParameter())
                    sortable?.let { operation.addParametersItem(sortParameter(it)) }
                }
            }
        }
        return operation
    }

    private fun sizeParameter() =
        queryParameter("size", "Items per page", IntegerSchema().minimum(BigDecimal.ONE).maximum(BigDecimal(policy.maxSize))._default(policy.defaultSize))

    private fun sortParameter(sortable: SortableFields) =
        repeatableQueryParameter(
            "sort",
            "Order: `field` or `field,desc`, repeatable. Sortable fields: ${sortable.fields}. " +
                "Default: ${sortable.defaultOrders.joinToString { "${it.field},${it.direction.name.lowercase()}" }}. " +
                "`${sortable.tieBreaker}` is always appended as the tie breaker.",
            StringSchema(),
        )

    private fun entityOf(parameter: MethodParameter): KClass<*>? {
        parameter.getParameterAnnotation(Paged::class.java)?.let { return it.entity }
        val method = parameter.method ?: return null
        return method.parameterAnnotations.asSequence().flatMap { it.asSequence() }
            .mapNotNull { annotation ->
                val attribute = runCatching { annotation.annotationClass.java.getMethod("entity") }.getOrNull()
                (attribute?.invoke(annotation) as? Class<*>)?.kotlin
            }
            .firstOrNull()
    }
}
