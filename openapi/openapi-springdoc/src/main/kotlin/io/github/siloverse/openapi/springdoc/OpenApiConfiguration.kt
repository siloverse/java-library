package io.github.siloverse.openapi.springdoc

import io.github.siloverse.pagination.core.keyset.KeysetRequest
import io.github.siloverse.pagination.core.offset.OffsetRequest
import io.github.siloverse.pagination.core.policy.PaginationPolicy
import io.github.siloverse.pagination.core.sort.SortRegistry
import io.github.siloverse.projector.core.Projection
import io.github.siloverse.selector.core.spec.SelectorRegistry
import io.github.siloverse.selector.spring.web.Selectors
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Import this next to the three libraries' configurations and springdoc describes a list endpoint
 * completely: every filter, `page`/`size` or `after`/`size`, `sort` with the sortable fields, and
 * `include`/`exclude` with the DTO's parts as an enum.
 *
 * springdoc would otherwise guess at our resolved parameter types and document a `selectors`
 * object or a `request` object; those types are told to springdoc as ignored.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    init {
        SpringDocUtils.getConfig()
            .addRequestWrapperToIgnore(OffsetRequest::class.java, KeysetRequest::class.java, Projection::class.java)
            .addAnnotationsToIgnore(Selectors::class.java)
    }

    @Bean
    fun selectorsOperationCustomizer(registry: SelectorRegistry): OperationCustomizer = SelectorsOperationCustomizer(registry)

    @Bean
    fun pagingOperationCustomizer(policy: ObjectProvider<PaginationPolicy>, sorts: SortRegistry): OperationCustomizer =
        PagingOperationCustomizer(policy.getIfAvailable { PaginationPolicy.DEFAULT }, sorts)

    @Bean
    fun projectionOperationCustomizer(): OperationCustomizer = ProjectionOperationCustomizer()
}
