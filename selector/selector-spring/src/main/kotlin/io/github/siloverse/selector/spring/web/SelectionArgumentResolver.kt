package io.github.siloverse.selector.spring.web

import io.github.siloverse.selector.core.definition.FilterDefinition
import io.github.siloverse.selector.core.filter.Selection
import org.springframework.beans.factory.BeanFactory
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Lets a controller method declare a `@Filtered("...") selection: Selection` parameter and
 * receive the query string already parsed and validated against that list's definition.
 *
 * The whole query string is handed to the parser; keys in [reserved] (paging and sort) are
 * skipped, every other key must be a declared filter. A `Selection` parameter without the
 * annotation is a programming error and says so.
 */
class SelectionArgumentResolver(
    private val beanFactory: BeanFactory,
    private val reserved: Set<String> = Selection.DEFAULT_RESERVED,
) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == Selection::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Selection {
        val filtered = parameter.getParameterAnnotation(Filtered::class.java)
            ?: throw IllegalStateException(
                "parameter '${parameter.parameterName}' of ${parameter.method} is a Selection but has no @Filtered(\"<definition bean>\")",
            )
        val definition = beanFactory.getBean(filtered.definition, FilterDefinition::class.java)
        val params = webRequest.parameterMap.mapValues { (_, values) -> values.toList() }
        return Selection.parse(definition, params, reserved)
    }
}
