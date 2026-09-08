package keswa.core.common

/**
 * UTC wall-clock time. Nothing in the app calls a platform time API directly — every timestamp
 * flows through this, so tests can control time and no code path silently depends on the local
 * timezone (see docs/sync-strategy.md D13).
 */
interface Clock {
    fun nowEpochMillis(): Long
}

object SystemClock : Clock {
    override fun nowEpochMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()
}

/** Test double: starts at [initialEpochMillis] and only moves when told to. */
class FixedClock(initialEpochMillis: Long) : Clock {
    private var epochMillis = initialEpochMillis
    override fun nowEpochMillis(): Long = epochMillis
    fun set(epochMillis: Long) { this.epochMillis = epochMillis }
    fun advanceBy(millis: Long) { epochMillis += millis }
}
