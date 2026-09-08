package keswa.core.common

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UlidTest {

    @Test
    fun `generated ids are the right length and alphabet`() {
        val id = Ulid.generate(1_700_000_000_000L)
        assertEquals(26, id.length)
        assertTrue(Ulid.isValid(id))
    }

    @Test
    fun `same timestamp, different randomness, produces different ids`() {
        val a = Ulid.generate(1_700_000_000_000L, Random(1))
        val b = Ulid.generate(1_700_000_000_000L, Random(2))
        assertTrue(a != b)
    }

    @Test
    fun `later timestamp sorts after earlier timestamp lexicographically`() {
        val earlier = Ulid.generate(1_700_000_000_000L, Random(42))
        val later = Ulid.generate(1_700_000_000_001L, Random(42))
        assertTrue(earlier < later)
    }

    @Test
    fun `isValid rejects wrong length and bad alphabet`() {
        assertTrue(!Ulid.isValid("TOO_SHORT"))
        assertTrue(!Ulid.isValid("I".repeat(26))) // 'I' is excluded from Crockford base32
    }

    @Test
    fun `monotonic factory is strictly increasing within the same millisecond`() {
        val clock = FixedClock(1_700_000_000_000L)
        val factory = MonotonicUlidFactory(clock, Random(7))

        val ids = List(500) { factory.next() }

        assertEquals(ids, ids.sorted(), "ids must already be in sorted order")
        assertEquals(ids.size, ids.toSet().size, "ids must be unique")
    }

    @Test
    fun `monotonic factory resets randomness when the millisecond advances`() {
        val clock = FixedClock(1_700_000_000_000L)
        val factory = MonotonicUlidFactory(clock, Random(7))

        val first = factory.next()
        clock.advanceBy(1)
        val second = factory.next()

        assertTrue(first < second)
        assertTrue(first.substring(0, 10) != second.substring(0, 10), "timestamp prefix should differ")
    }
}
