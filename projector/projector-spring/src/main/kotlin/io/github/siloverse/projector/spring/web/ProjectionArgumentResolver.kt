package io.github.siloverse.projector.spring.web

import io.github.siloverse.projector.core.Projection
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/** Lets a controller declare a `projection: Projection` parameter, read from `include` and `exclude`. */
class ProjectionArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == Projection::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Projection =
        Projection.parse(
            include = webRequest.getParameterValues(Projection.INCLUDE)?.toList(),
            exclude = webRequest.getParameterValues(Projection.EXCLUDE)?.toList(),
        )
}
