package io.github.siloverse.openapi.springdoc

import io.github.siloverse.selector.core.spec.SelectorRegistry
import io.github.siloverse.selector.core.spec.SelectorSpec
import io.github.siloverse.selector.spring.web.Selectors
import io.swagger.v3.oas.models.Operation
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.web.method.HandlerMethod

/**
 * Documents every filter an endpoint accepts: for a `@Selectors(entity = X)` parameter, one query
 * parameter per spec registered for X, named by its key, typed by its value type, repeatable when
 * the spec takes many values. The client generator turns each into a typed Kotlin parameter.
 */
class SelectorsOperationCustomizer(private val registry: SelectorRegistry) : OperationCustomizer {

    override fun customize(operation: Operation, handlerMethod: HandlerMethod): Operation {
        for (parameter in handlerMethod.methodParameters) {
            val selectors = parameter.getParameterAnnotation(Selectors::class.java) ?: continue
            registry.specs
                .filter { it.entityType == selectors.entity }
                .sortedBy { it.key }
                .forEach { spec -> operation.addParametersItem(parameterFor(spec)) }
        }
        return operation
    }

    private fun parameterFor(spec: SelectorSpec<*>) =
        if (spec.arity == SelectorSpec.Arity.MULTI) {
            repeatableQueryParameter(spec.key, "Filter: ${spec.type.name}, one or more values (repeat the parameter)", schemaFor(spec.type))
        } else {
            queryParameter(spec.key, "Filter: one ${spec.type.name}", schemaFor(spec.type))
        }
}
