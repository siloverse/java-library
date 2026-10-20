package io.github.siloverse.pagination.spring.config

import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.core.sort.SortContract
import io.github.siloverse.pagination.core.sort.SortRegistry
import io.github.siloverse.pagination.spring.web.PaginationArgumentResolver
import io.github.siloverse.pagination.spring.web.PaginationExceptionHandler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Import this in a web application to get the argument resolver and the 400 mapping.
 *
 * Declare a [PaginationPolicy] bean to set your own default and maximum size; without one,
 * [PaginationPolicy.DEFAULT] applies. Every [SortContract] bean (a `SortSpec` per entity) is
 * collected into the [SortRegistry] that validates `sort=`.
 *
 * `proxyBeanMethods = false` because Kotlin classes are final and Spring would otherwise try
 * to subclass this one. Nothing is lost: no bean method here calls another bean method.
 */
@Configuration(proxyBeanMethods = false)
class PaginationConfiguration {

    @Bean
    fun sortRegistry(contracts: ObjectProvider<SortContract>): SortRegistry = SortRegistry(contracts.toList())

    @Bean
    fun paginationArgumentResolver(policy: ObjectProvider<PaginationPolicy>, sorts: SortRegistry): PaginationArgumentResolver =
        PaginationArgumentResolver(policy.getIfAvailable { PaginationPolicy.DEFAULT }, sorts)

    @Bean
    fun paginationWebMvcConfigurer(resolver: PaginationArgumentResolver): WebMvcConfigurer =
        object : WebMvcConfigurer {
            override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
                resolvers.add(resolver)
            }
        }

    @Bean
    fun paginationExceptionHandler(): PaginationExceptionHandler = PaginationExceptionHandler()
}
