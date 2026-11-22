package io.github.siloverse.identifiers.core

import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

object UuidV7 {
    private val random = SecureRandom()

    fun next(now: Instant = Instant.now()): UUID {
        val millis = now.toEpochMilli()
        val high = (millis shl 16) or 0x7000L or (random.nextLong() and 0x0FFFL)
        val low = (random.nextLong() and 0x3FFFFFFFFFFFFFFFL) or Long.MIN_VALUE
        return UUID(high, low)
    }
}