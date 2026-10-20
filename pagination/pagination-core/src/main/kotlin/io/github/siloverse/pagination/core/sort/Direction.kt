package io.github.siloverse.pagination.core.sort

enum class Direction {
    ASC,
    DESC,
    ;

    companion object {
        /** `asc` / `desc`, any case. Null for anything else; the caller decides the error. */
        fun fromText(text: String): Direction? = entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
    }
}
