package io.github.siloverse.pagination.core.keyset

import io.github.siloverse.pagination.core.error.PaginationException
import io.github.siloverse.pagination.core.sort.Direction

/**
 * The order of one list, as the keys the cursor is made of. This IS the keyset: the cursor
 * holds exactly these columns' values from the last row, and the "after" predicate is built
 * from exactly this sort, so the two cannot drift apart.
 *
 * The last key must be unique on its own (usually `id`), or rows that share the other values
 * are skipped between pages. Build keys from a client's sort through
 * [io.github.siloverse.pagination.core.sort.SortableFields.keysFor], which guarantees that.
 */
class KeysetKeys(val keys: List<KeysetKey<*>>) {

    init {
        require(keys.isNotEmpty()) { "a keyset needs at least one sort column" }
        require(keys.map { it.property }.distinct().size == keys.size) { "a sort column appears twice" }
    }

    constructor(vararg keys: KeysetKey<*>) : this(keys.toList())

    val properties: List<String>
        get() = keys.map { it.property }

    /** True when every key sorts the same way; then the "after" predicate can be a single row-value comparison. */
    val sameDirection: Boolean
        get() = keys.map { it.direction }.distinct().size == 1

    val direction: Direction
        get() = keys.first().direction

    /** `price:desc,id:asc` — the first part of every cursor built from these keys. */
    val signature: String
        get() = keys.joinToString(",") { "${it.property}:${it.direction.name.lowercase()}" }

    /**
     * The typed key values a client's cursor carries, in key order. A cursor built for another
     * sort, or for another list, is a client error: its values would not mean a position here.
     */
    fun decode(cursor: Cursor): List<Any> {
        val all = cursor.parts()
        if (all.first() != signature) throw PaginationException("after", "was made for sort '${all.first()}', not '$signature'")
        if (all.size != keys.size + 1) throw PaginationException("after", "does not belong to this list")
        val parts = all.drop(1)
        return keys.zip(parts).map { (key, part) ->
            try {
                key.decode(part)
            } catch (e: RuntimeException) {
                throw PaginationException("after", "is not a valid cursor")
            }
        }
    }

    /** The cursor for a row, from its key values in key order, as the database returned them. */
    fun cursorOf(values: List<Any>): Cursor {
        require(values.size == keys.size) { "expected ${keys.size} key values, got ${values.size}" }
        return Cursor.of(listOf(signature) + keys.zip(values).map { (key, value) -> key.encodeAny(value) })
    }
}
