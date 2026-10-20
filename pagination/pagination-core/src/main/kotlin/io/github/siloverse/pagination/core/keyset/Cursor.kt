package io.github.siloverse.pagination.core.keyset

import java.util.Base64

/**
 * A bookmark that says "the last row I saw". The client receives it in one response
 * and sends it back in the next request. The client never builds one.
 *
 * Inside, a cursor is the sort key values of that last row, as strings, in sort order.
 * On the wire it is one opaque string. Each part is base64url encoded and the parts
 * are joined with a dot, so a part may hold any character without breaking the split.
 *
 * Converting typed values (Instant, UUID, Long) to and from strings is the adapter's job,
 * not the cursor's. The cursor only carries them safely.
 */
@JvmInline
value class Cursor(val value: String) {


    init {
        require(value.isNotBlank()) { "cursor must not be blank" }
    }

    /** The sort key values this cursor was built from, in sort order. */
    fun parts(): List<String> =
        value.split(SEPARATOR).map { String(DECODER.decode(it), Charsets.UTF_8) }

    companion object {
        private const val SEPARATOR = "."
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()

        /** Builds a cursor from sort key values, in sort order. At least one part is required. */
        fun of(vararg parts: String): Cursor = of(parts.toList())

        fun of(parts: List<String>): Cursor {
            require(parts.isNotEmpty()) { "cursor needs at least one sort key value" }
            return Cursor(parts.joinToString(SEPARATOR) { ENCODER.encodeToString(it.toByteArray(Charsets.UTF_8)) })
        }
    }
}
