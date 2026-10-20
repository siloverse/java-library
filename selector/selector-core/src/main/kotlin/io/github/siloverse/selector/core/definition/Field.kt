package io.github.siloverse.selector.core.definition

/**
 * One field a client may touch, and how: which filter operators, and whether it can sort.
 *
 * A field that allows nothing is a mistake, so it is rejected at construction.
 */
class Field<T : Any>(
    val name: String,
    val type: FieldType<T>,
    val operators: Set<Operator> = emptySet(),
    val sortable: Boolean = false,
) {

    init {
        require(name.isNotBlank()) { "field name must not be blank" }
        require(name.none { it == '.' || it == ',' || it.isWhitespace() }) { "field name '$name' may not contain '.', ',' or spaces" }
        require(operators.isNotEmpty() || sortable) { "field '$name' allows neither filtering nor sorting" }
    }

    val filterable: Boolean
        get() = operators.isNotEmpty()

    override fun toString(): String = name
}
