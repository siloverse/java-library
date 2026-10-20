package io.github.siloverse.selector.core.definition

/**
 * One field a client may filter by, and with which operators.
 *
 * A field with no operators allows nothing, so it is rejected at construction.
 */
class Field<T : Any>(
    val name: String,
    val type: FieldType<T>,
    val operators: Set<Operator>,
) {

    constructor(name: String, type: FieldType<T>, vararg operators: Operator) : this(name, type, operators.toSet())

    init {
        require(name.isNotBlank()) { "field name must not be blank" }
        require(name.none { it == '.' || it == ',' || it.isWhitespace() }) { "field name '$name' may not contain '.', ',' or spaces" }
        require(operators.isNotEmpty()) { "field '$name' allows no operator" }
    }

    override fun toString(): String = name
}
