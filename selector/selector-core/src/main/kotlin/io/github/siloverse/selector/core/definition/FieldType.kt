package io.github.siloverse.selector.core.definition

import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

/**
 * How a field's value is read from the query string. The client sends text; the list says
 * what that text must be. Parsing happens once, here, and never again downstream.
 */
class FieldType<T : Any>(val name: String, private val parser: (String) -> T) {

    /** Throws [IllegalArgumentException] with a plain message; the caller turns it into a 400. */
    fun parse(raw: String): T =
        try {
            parser(raw)
        } catch (e: RuntimeException) {
            throw IllegalArgumentException("'$raw' is not a valid $name")
        }

    override fun toString(): String = name

    companion object {
        val STRING = FieldType("string") { it }
        val INT = FieldType("int") { it.toInt() }
        val LONG = FieldType("long") { it.toLong() }
        val BOOLEAN = FieldType("boolean") {
            when (it.lowercase()) {
                "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException()
            }
        }
        val UUID = FieldType("uuid") { java.util.UUID.fromString(it) }
        val INSTANT = FieldType("instant") { Instant.parse(it) }
        val OFFSET_DATE_TIME = FieldType("date-time") { OffsetDateTime.parse(it) }
    }
}
