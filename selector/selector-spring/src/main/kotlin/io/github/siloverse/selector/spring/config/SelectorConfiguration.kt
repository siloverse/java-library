package io.github.siloverse.selector.spring.config

import io.github.siloverse.selector.spring.web.SelectionArgumentResolver
import io.github.siloverse.selector.spring.web.SelectorExceptionHandler
import org.springframework.beans.factory.BeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Import this in a web application to get the `@Filtered` argument resolver and the 400 mapping.
 * Declare one `FilterDefinition` bean per list and name it in the controller's annotation.
 *
 * `proxyBeanMethods = false` because Kotlin classes are final; no bean method here calls another.
 */
@Configuration(proxyBeanMethods = false)
class SelectorConfiguration {

    @Bean
    fun selectionArgumentResolver(beanFactory: BeanFactory): SelectionArgumentResolver = SelectionArgumentResolver(beanFactory)

    @Bean
    fun selectorWebMvcConfigurer(resolver: SelectionArgumentResolver): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(resolver)
            }
        }

    @Bean
    fun selectorExceptionHandler(): SelectorExceptionHandler = SelectorExceptionHandler()
}
