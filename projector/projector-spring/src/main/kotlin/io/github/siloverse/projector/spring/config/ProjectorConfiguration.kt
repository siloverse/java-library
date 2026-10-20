package io.github.siloverse.projector.spring.config

import io.github.siloverse.projector.core.Projector
import io.github.siloverse.projector.core.ProjectorManager
import io.github.siloverse.projector.core.ProjectorRegistry
import io.github.siloverse.projector.spring.web.ProjectionArgumentResolver
import io.github.siloverse.projector.spring.web.ProjectorExceptionHandler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Import this in a web application: every [Projector] bean goes into the registry, the manager
 * is ready to inject into mappers, controllers may declare a `Projection` parameter, and a bad
 * projection is a 400. `proxyBeanMethods = false` because Kotlin classes are final.
 */
@Configuration(proxyBeanMethods = false)
class ProjectorConfiguration {

    @Bean
    fun projectorRegistry(projectors: ObjectProvider<Projector<*, *>>): ProjectorRegistry = ProjectorRegistry(projectors.toList())

    @Bean
    fun projectorManager(registry: ProjectorRegistry): ProjectorManager = ProjectorManager(registry)

    @Bean
    fun projectionArgumentResolver(): ProjectionArgumentResolver = ProjectionArgumentResolver()

    @Bean
    fun projectorWebMvcConfigurer(resolver: ProjectionArgumentResolver): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(resolver)
            }
        }

    @Bean
    fun projectorExceptionHandler(): ProjectorExceptionHandler = ProjectorExceptionHandler()
}
