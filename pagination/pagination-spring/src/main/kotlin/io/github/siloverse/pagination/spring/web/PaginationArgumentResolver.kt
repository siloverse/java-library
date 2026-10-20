package io.github.siloverse.pagination.spring.web

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.core.sort.SortRegistry
import io.github.siloverse.pagination.core.sort.SortRequest
import io.github.siloverse.pagination.core.sort.SortableFields
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import kotlin.reflect.KClass

/**
 * Lets a controller method declare an [OffsetRequest] or [KeysetRequest] parameter and
 * receive it already validated: page, size, cursor and sort.
 *
 * Reads `page`, `size`, `after` and `sort`. Turning a raw string into an Int happens here;
 * whether the Int is acceptable is the [PaginationPolicy]'s call; whether a sort field is
 * allowed is the entity's [io.github.siloverse.pagination.core.sort.SortContract]'s call.
 *
 * The entity comes from [Paged] on the parameter, or from any annotation on any parameter of
 * the same method that has an `entity` attribute (the selector's `@Selectors(entity = ...)`),
 * so a controller names its entity once. A list with no contract has a fixed order: a `sort`
 * parameter for it is a 400. Keyset paging without a contract is a programming error.
 */
class PaginationArgumentResolver(
    private val policy: PaginationPolicy,
    private val sorts: SortRegistry,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == OffsetRequest::class.java ||
            parameter.parameterType == KeysetRequest::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any {
        val size = webRequest.intParameter(SIZE)
        val sort = resolveSort(parameter, webRequest)
        return when (parameter.parameterType) {
            OffsetRequest::class.java -> policy.offset(page = webRequest.intParameter(PAGE), size = size, sort = sort)
            KeysetRequest::class.java -> policy.keyset(
                after = webRequest.getParameter(AFTER),
                size = size,
                sort = sort ?: throw IllegalStateException(
                    "keyset paging in ${parameter.method} needs a sort contract: name the entity with @Paged or @Selectors and declare a SortSpec bean for it",
                ),
            )
            else -> error("unsupported parameter type ${parameter.parameterType}")
        }
    }

    private fun resolveSort(parameter: MethodParameter, webRequest: NativeWebRequest): SortRequest? {
        val raw = webRequest.getParameterValues(SORT)?.toList()
        val sortable: SortableFields? = entityOf(parameter)?.let { sorts.sortableFor(it) }
        return when {
            sortable != null -> SortRequest.parse(sortable, raw)
            raw != null -> throw PaginationException(SORT, "this list has a fixed order and cannot be sorted")
            else -> null
        }
    }

    /** `@Paged` on this parameter first; otherwise the first `entity` attribute on any annotation of any parameter. */
    private fun entityOf(parameter: MethodParameter): KClass<*>? {
        parameter.getParameterAnnotation(Paged::class.java)?.let { return it.entity }
        val method = parameter.method ?: return null
        return method.parameterAnnotations.asSequence().flatMap { it.asSequence() }
            .mapNotNull { annotation ->
                val attribute = runCatching { annotation.annotationClass.java.getMethod(ENTITY_ATTRIBUTE) }.getOrNull()
                (attribute?.invoke(annotation) as? Class<*>)?.kotlin
            }
            .firstOrNull()
    }

    private fun NativeWebRequest.intParameter(name: String): Int? {
        val raw = getParameter(name) ?: return null
        return raw.toIntOrNull() ?: throw PaginationException(name, "must be a whole number, was '$raw'")
    }

    companion object {
        const val PAGE = "page"
        const val SIZE = "size"
        const val AFTER = "after"
        const val SORT = "sort"
        const val ENTITY_ATTRIBUTE = "entity"
    }
}
