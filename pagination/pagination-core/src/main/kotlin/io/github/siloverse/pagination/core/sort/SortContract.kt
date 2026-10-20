package io.github.siloverse.pagination.core.sort

import kotlin.reflect.KClass

/**
 * The order contract of one entity: which fields a client may sort by. Engine adapters add
 * how to reach the columns; this is the part the web layer needs to validate `sort=`.
 */
interface SortContract {
    val entityType: KClass<*>
    val sortable: SortableFields
}
