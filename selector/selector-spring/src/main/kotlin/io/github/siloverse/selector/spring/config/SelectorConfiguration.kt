package io.github.siloverse.selector.spring.config

import io.github.siloverse.selector.core.spec.SelectorRegistry
import io.github.siloverse.selector.core.spec.SelectorSpec
import io.github.siloverse.selector.spring.web.SelectorExceptionHandler
import io.github.siloverse.selector.spring.web.SelectorsArgumentResolver
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Import this in a web application to get the `@Selectors` argument resolver and the 400 mapping.
 * Every [SelectorSpec] bean is collected into the [SelectorRegistry]; the engine's manager (for
 * JDSL, `SelectorSpecManager(specs)`) is declared by the application from the same beans.
 *
 * `proxyBeanMethods = false` because Kotlin classes are final; no bean method here calls another.
 */
@Configuration(proxyBeanMethods = false)
class SelectorConfiguration {

    @Bean
    fun selectorRegistry(specs: ObjectProvider<SelectorSpec<*>>): SelectorRegistry = SelectorRegistry(specs.toList())

    @Bean
    fun selectorsArgumentResolver(registry: SelectorRegistry): SelectorsArgumentResolver = SelectorsArgumentResolver(registry)

    @Bean
    fun selectorWebMvcConfigurer(resolver: SelectorsArgumentResolver): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(resolver)
            }
        }

    @Bean
    fun selectorExceptionHandler(): SelectorExceptionHandler = SelectorExceptionHandler()
}
