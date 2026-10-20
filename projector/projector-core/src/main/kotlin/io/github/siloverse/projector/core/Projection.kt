package io.github.siloverse.projector.core

/**
 * Which optional parts the client wants: `include=items,customer` switches parts on,
 * `exclude=items` switches a default part off. No parameters means the defaults.
 *
 * Names are not checked here: the manager checks them against the DTO it projects, which is
 * the only place the DTO's names are known.
 */
data class Projection(val include: Set<String> = emptySet(), val exclude: Set<String> = emptySet()) {

    fun isActive(projectable: Projectable): Boolean =
        projectable.name in include || (projectable.isDefault && projectable.name !in exclude)

    /** Every name the client mentioned, for the manager to validate. */
    val names: Set<String>
        get() = include + exclude

    companion object {
        val DEFAULTS = Projection()
        const val INCLUDE = "include"
        const val EXCLUDE = "exclude"

        /** [include] and [exclude] are every value the client sent for each; values may be comma lists. */
        fun parse(include: List<String>?, exclude: List<String>?): Projection =
            Projection(names(INCLUDE, include), names(EXCLUDE, exclude))

        private fun names(parameter: String, raw: List<String>?): Set<String> =
            raw.orEmpty()
                .flatMap { it.split(",") }
                .map { it.trim() }
                .onEach { if (it.isEmpty()) throw ProjectorException(parameter, "has an empty name") }
                .toSet()
    }
}
