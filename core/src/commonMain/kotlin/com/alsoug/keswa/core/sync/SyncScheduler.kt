package com.alsoug.keswa.core.sync

import com.alsoug.keswa.core.coroutines.rethrowIfCancellation
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.LogLevel
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the engine in the background, and backs off when the shop's wifi does.
 *
 * **KD-009 re-decides ADR-041 for this path and this path only.** Cashi banned HTTP retry for
 * native parity (ADR-036), which is declared not applicable here — but the reason retry is usually
 * dangerous still is: a request that may have taken effect before the connection dropped must not
 * be repeated. This phase engineered that away. Every row is keyed on a client-generated UUID and
 * every write is an upsert, so pushing twice is pushing once.
 *
 * Nothing user-facing retries. A person waiting at a counter wants a failure and a button, not a
 * silent thirty-second stall.
 *
 * Full jitter rather than plain exponential: three tills in a shop that loses its router would
 * otherwise retry in lockstep for ever, and arrive together the moment it comes back.
 */
class SyncScheduler(
    private val engine: SyncEngine,
    private val scope: CoroutineScope,
    private val platform: IPlatformProvider,
    private val random: Random = Random.Default,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {

    fun start(intervalMillis: Long = INTERVAL): Job = scope.launch {
        while (isActive) {
            syncWithRetry()
            sleep(intervalMillis)
        }
    }

    /**
     * Returns the outcome of the first attempt that succeeds, or the last failure.
     *
     * A device the server has refused stops immediately rather than backing off: a revoked token is
     * not a network problem and no amount of waiting will change it. Its outbox is untouched, so
     * nothing it did is lost while somebody sorts the enrolment out.
     */
    suspend fun syncWithRetry(maxAttempts: Int = MAX_ATTEMPTS): Result<SyncOutcome> {
        var attempt = 0
        while (true) {
            val result = engine.syncOnce()
            result.onSuccess { return result }

            val failure = result.exceptionOrNull()
            failure?.rethrowIfCancellation()

            if (failure is SyncNotAuthorised) {
                platform.log(LogLevel.WARNING, TAG, "sync refused: ${failure.message}")
                return result
            }

            attempt++
            if (attempt >= maxAttempts) {
                platform.log(LogLevel.WARNING, TAG, "sync gave up after $attempt attempts", failure)
                return result
            }

            sleep(backoffMillis(attempt))
        }
    }

    /** Full jitter: anywhere between nothing and the current ceiling. */
    internal fun backoffMillis(attempt: Int): Long {
        val ceiling = min(MAX_BACKOFF, BASE_BACKOFF shl (attempt - 1))
        return random.nextLong(ceiling + 1)
    }

    private companion object {
        const val TAG = "Sync"
        const val MAX_ATTEMPTS = 5
        const val BASE_BACKOFF = 1_000L
        const val MAX_BACKOFF = 60_000L

        /**
         * A till that learns about a price change within a minute is a till that is working. Real
         * time would cost a reconnection state machine and buy nothing a shop has asked for.
         */
        const val INTERVAL = 60_000L
    }
}
