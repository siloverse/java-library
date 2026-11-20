package io.github.siloverse.identifiers.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class UuidV7Test {

    private val now = Instant.parse("2026-11-20T10:15:30.123Z")

    @Test
    fun `an id is a version 7 uuid of the standard variant`() {
        val id = UuidV7.next()

        assertThat(id.version()).isEqualTo(7)
        assertThat(id.variant()).isEqualTo(2)
    }

    @Test
    fun `the first 48 bits are the milliseconds of the instant`() {
        val id = UuidV7.next(now)

        assertThat(id.mostSignificantBits ushr 16).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun `an id made later sorts after an id made earlier`() {
        val earlier = UuidV7.next(now)
        val later = UuidV7.next(now.plusMillis(1))

        // Compared as text, which is the byte order Postgres uses for the uuid type.
        assertThat(later.toString()).isGreaterThan(earlier.toString())
    }

    @Test
    fun `ids made in the same millisecond share the time part and differ in the random part`() {
        val a = UuidV7.next(now)
        val b = UuidV7.next(now)

        assertThat(a.toString().substring(0, 13)).isEqualTo(b.toString().substring(0, 13))
        assertThat(a).isNotEqualTo(b)
    }

    @Test
    fun `ten thousand ids in a row are all different`() {
        val ids = (1..10_000).map { UuidV7.next() }

        assertThat(ids.toSet()).hasSize(10_000)
    }
}
