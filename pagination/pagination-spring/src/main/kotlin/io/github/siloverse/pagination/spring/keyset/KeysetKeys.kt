package io.github.siloverse.pagination.spring.keyset

import org.springframework.data.domain.Sort
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * One sort column of a list, and how its value travels through the cursor as a string.
 *
 * The cursor only carries strings. The developer knows that `createdAt` is an [OffsetDateTime]
 * and `id` is a [UUID]; this is where that knowledge is written down, once per list.
 */
class KeysetKey<T : Any>(
    val property: String,
    val direction: Sort.Direction = Sort.Direction.ASC,
    val encode: (T) -> String,
    val decode: (String) -> T,
) {
    @Suppress("UNCHECKED_CAST")
    internal fun encodeAny(value: Any): String = encode(value as T)

    companion object {
        fun uuid(property: String, direction: Sort.Direction = Sort.Direction.ASC) =
            KeysetKey(property, direction, UUID::toString, UUID::fromString)

        fun long(property: String, direction: Sort.Direction = Sort.Direction.ASC) =
            KeysetKey(property, direction, Long::toString, String::toLong)

        fun string(property: String, direction: Sort.Direction = Sort.Direction.ASC) =
            KeysetKey<String>(property, direction, { it }, { it })

        fun instant(property: String, direction: Sort.Direction = Sort.Direction.ASC) =
            KeysetKey(property, direction, Instant::toString, Instant::parse)

        fun offsetDateTime(property: String, direction: Sort.Direction = Sort.Direction.ASC) =
            KeysetKey(property, direction, OffsetDateTime::toString, OffsetDateTime::parse)
    }
}

/**
 * The sort of one list, in order. This IS the keyset: the cursor holds exactly these columns'
 * values from the last row, and Spring Data builds the "after" predicate from exactly this sort.
 *
 * The last key must be unique on its own (usually `id`), or rows that share the other values
 * are skipped between pages. Spring Data does not check that; this class trusts the developer.
 */
class KeysetKeys(val keys: List<KeysetKey<*>>) {

    init {
        require(keys.isNotEmpty()) { "a keyset needs at least one sort column" }
        require(keys.map { it.property }.distinct().size == keys.size) { "a sort column appears twice" }
    }

    constructor(vararg keys: KeysetKey<*>) : this(keys.toList())

    /** The sort to pass to the query. Same columns, same order, same directions as the cursor. */
    fun sort(): Sort = Sort.by(keys.map { Sort.Order(it.direction, it.property) })
}
