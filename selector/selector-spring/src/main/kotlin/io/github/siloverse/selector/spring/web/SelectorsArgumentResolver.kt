package io.github.siloverse.selector.spring.web

import io.github.siloverse.selector.core.selector.Selector
import io.github.siloverse.selector.core.selector.Selectors as SelectorParser
import io.github.siloverse.selector.core.spec.SelectorRegistry
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * Lets a controller method declare `@Selectors(entity = X::class) selectors: List<Selector>`
 * and receive every `filter.*` query key as a selector, each already known to the registry.
 * An unknown filter is refused here, at the door, before the controller runs; values are
 * parsed later by the spec that executes them.
 */
class SelectorsArgumentResolver(private val registry: SelectorRegistry) : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(Selectors::class.java)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): List<Selector> {
        val entity = parameter.getParameterAnnotation(Selectors::class.java)!!.entity
        val params = webRequest.parameterMap.mapValues { (_, values) -> values.toList() }
        return SelectorParser.parse(entity, params).onEach { registry.specFor(it) }
    }
}
