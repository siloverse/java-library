package io.github.siloverse.pagination.spring.web

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.policy.PaginationPolicy

import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Lets a controller method declare an [OffsetRequest] or [KeysetRequest] parameter and
 * receive it already validated.
 *
 * Reads the query parameters `page`, `size` and `after`. Turning a raw string into an Int
 * happens here; deciding whether that Int is acceptable happens in the [PaginationPolicy].
 * A missing parameter is passed to the policy as null. An empty one (`?size=`) is not a
 * whole number and is rejected like any other bad value.
 */
class PaginationArgumentResolver(private val policy: PaginationPolicy) : HandlerMethodArgumentResolver {

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
        return when (parameter.parameterType) {
            OffsetRequest::class.java -> policy.offset(page = webRequest.intParameter(PAGE), size = size)
            KeysetRequest::class.java -> policy.keyset(after = webRequest.getParameter(AFTER), size = size)
            else -> error("unsupported parameter type ${parameter.parameterType}")
        }
    }

    private fun NativeWebRequest.intParameter(name: String): Int? {
        val raw = getParameter(name) ?: return null
        return raw.toIntOrNull() ?: throw PaginationException(name, "must be a whole number, was '$raw'")
    }

    companion object {
        const val PAGE = "page"
        const val SIZE = "size"
        const val AFTER = "after"
    }
}
