package io.github.siloverse.projector.core

/**
 * Marks an optional part of a response DTO. The client switches it on with `include=<name>`;
 * a default part is on unless the client sends `exclude=<name>`.
 *
 * Only the backing field may carry it (so Kotlin puts it on the field), which is where the
 * manager reads it and writes the projected value.
 *
 * ```
 * data class OrderDto(val id: UUID, @Projectable("items") val items: List<ItemDto>? = null)
 * ```
 */
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Projectable(val name: String, val isDefault: Boolean = false)
