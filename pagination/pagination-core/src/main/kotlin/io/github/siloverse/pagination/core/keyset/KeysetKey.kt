package io.github.siloverse.pagination.core.keyset

import io.github.siloverse.pagination.core.sort.Direction
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
    val direction: Direction = Direction.ASC,
    val encode: (T) -> String,
    val decode: (String) -> T,
) {

    fun withDirection(direction: Direction): KeysetKey<T> = KeysetKey(property, direction, encode, decode)

    @Suppress("UNCHECKED_CAST")
    internal fun encodeAny(value: Any): String = encode(value as T)

    override fun toString(): String = "$property ${direction.name.lowercase()}"

    companion object {
        fun uuid(property: String, direction: Direction = Direction.ASC) =
            KeysetKey(property, direction, UUID::toString, UUID::fromString)

        fun int(property: String, direction: Direction = Direction.ASC) =
            KeysetKey(property, direction, Int::toString, String::toInt)

        fun long(property: String, direction: Direction = Direction.ASC) =
            KeysetKey(property, direction, Long::toString, String::toLong)

        fun string(property: String, direction: Direction = Direction.ASC) =
            KeysetKey<String>(property, direction, { it }, { it })

        fun instant(property: String, direction: Direction = Direction.ASC) =
            KeysetKey(property, direction, Instant::toString, Instant::parse)

        fun offsetDateTime(property: String, direction: Direction = Direction.ASC) =
            KeysetKey(property, direction, OffsetDateTime::toString, OffsetDateTime::parse)
    }
}
