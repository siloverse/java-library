package io.github.siloverse.selector.core.definition

/**
 * The filter contract of one list endpoint: the fields a client may filter by, each with
 * its type and allowed operators.
 *
 * Written once, next to the list. The filter parser reads from it; it is also the list's
 * documentation. Sorting is not here on purpose: the selector library builds the WHERE
 * clause and nothing else. The order of a list belongs to pagination.
 */
class FilterDefinition(val fields: List<Field<*>>) {

    constructor(vararg fields: Field<*>) : this(fields.toList())

    private val byName: Map<String, Field<*>> = fields.associateBy { it.name }

    init {
        require(fields.isNotEmpty()) { "a filter definition needs at least one field" }
        require(byName.size == fields.size) { "a field name appears twice: ${fields.map { it.name }}" }
    }

    fun field(name: String): Field<*>? = byName[name]

    val fieldNames: List<String>
        get() = fields.map { it.name }
}
