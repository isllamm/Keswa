package keswa.core.common

import kotlin.random.Random

/**
 * ULID: a 26-character, lexicographically sortable identifier — 48-bit UTC millisecond
 * timestamp followed by 80 bits of randomness, Crockford base32 encoded. Every primary key in
 * the app is one of these, generated client-side (ADR-001) — never an auto-increment column.
 */
object Ulid {
    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val MASK_5BIT = 0x1FL
    private const val MASK_40BIT = 0xFFFFFFFFFFL
    const val LENGTH = 26

    /** One-shot, non-monotonic generation. Prefer [MonotonicUlidFactory] for real ID generation. */
    fun generate(epochMillis: Long, random: Random = Random.Default): String =
        encode(epochMillis, random.nextLong() and MASK_40BIT, random.nextLong() and MASK_40BIT)

    fun isValid(value: String): Boolean =
        value.length == LENGTH && value.all { CROCKFORD.indexOf(it) >= 0 }

    internal fun encode(epochMillis: Long, randHi40: Long, randLo40: Long): String {
        require(epochMillis in 0..MAX_TIMESTAMP) { "ULID timestamp out of 48-bit range: $epochMillis" }
        val chars = CharArray(LENGTH)

        // 10 chars / 48 bits: first char carries only the top 3 bits (10*5 - 48 = 2 spare bits).
        chars[0] = CROCKFORD[((epochMillis ushr 45) and 0x07).toInt()]
        for (i in 1..9) {
            chars[i] = CROCKFORD[((epochMillis ushr (45 - 5 * i)) and MASK_5BIT).toInt()]
        }
        // 16 chars / 80 bits of randomness: two 40-bit halves, 8 chars each.
        for (i in 0..7) {
            chars[10 + i] = CROCKFORD[((randHi40 ushr (35 - 5 * i)) and MASK_5BIT).toInt()]
        }
        for (i in 0..7) {
            chars[18 + i] = CROCKFORD[((randLo40 ushr (35 - 5 * i)) and MASK_5BIT).toInt()]
        }
        return chars.concatToString()
    }

    private const val MAX_TIMESTAMP = 0xFFFFFFFFFFFFL // 48 bits
}

/**
 * Generates strictly increasing ULIDs even within the same millisecond, per the ULID monotonic
 * extension: a call that lands in the same millisecond as the previous one increments the random
 * part instead of drawing fresh randomness. Not thread-safe by design — callers go through
 * `UnitOfWork`'s single-writer dispatcher (docs/architecture.md §4), so no lock is needed here.
 */
class MonotonicUlidFactory(
    private val clock: Clock,
    private val random: Random = Random.Default,
) {
    private var lastMillis = -1L
    private var lastHi40 = 0L
    private var lastLo40 = 0L

    fun next(): String {
        val millis = clock.nowEpochMillis()
        if (millis == lastMillis) {
            if (lastLo40 == MASK_40BIT) {
                lastLo40 = 0L
                lastHi40 = (lastHi40 + 1) and MASK_40BIT
            } else {
                lastLo40 += 1
            }
        } else {
            lastMillis = millis
            lastHi40 = random.nextLong() and MASK_40BIT
            lastLo40 = random.nextLong() and MASK_40BIT
        }
        return Ulid.encode(millis, lastHi40, lastLo40)
    }

    private companion object {
        const val MASK_40BIT = 0xFFFFFFFFFFL
    }
}
