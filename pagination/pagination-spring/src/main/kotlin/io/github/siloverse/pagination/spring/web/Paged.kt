package io.github.siloverse.pagination.spring.web

import kotlin.reflect.KClass

/**
 * Names the entity whose sort contract validates `sort=` for an `OffsetRequest` or
 * `KeysetRequest` parameter. Needed only when no other parameter of the same method already
 * names the entity: any annotation with an `entity` attribute, such as the selector's
 * `@Selectors(entity = ...)`, is read first.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Paged(val entity: KClass<*>)
