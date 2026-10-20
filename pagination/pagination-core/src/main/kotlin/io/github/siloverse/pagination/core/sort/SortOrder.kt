package io.github.siloverse.pagination.core.sort

/** One column of an ORDER BY. */
data class SortOrder(val field: String, val direction: Direction = Direction.ASC)
