package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.coroutines.RealDispatchers
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.sync.LogApplier
import com.alsoug.keswa.core.sync.SyncEngine
import com.alsoug.keswa.core.sync.SyncNotAuthorised
import com.alsoug.keswa.core.sync.SyncScheduler
import com.alsoug.keswa.core.sync.SyncSettings
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * KD-009 — retry, but only here, and only so far.
 */
class SyncSchedulerTest {

    private val database = createTestDatabase()
    private val api = FakeSyncApi()
    private val tokens = FakeTokenStore("a-token")
    private val settings = SyncSettings(database.settingDao(), UuidIdGenerator())
    private val platform = FakePlatform()
    private val slept = mutableListOf<Long>()

    private val scheduler = SyncScheduler(
        engine = SyncEngine(
            database = database,
            api = api,
            applier = LogApplier(database, UuidIdGenerator(), { 0L }),
            settings = settings,
            tokens = tokens,
            platform = platform,
        ),
        scope = CoroutineScope(RealDispatchers.io),
        platform = platform,
        random = Random(1),
        sleep = { slept += it },
    )

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `a flaky connection is retried until it works`() = runBlocking {
        settings.setServerUrl("http://shop.local:8080")
        api.failures = 2

        val result = scheduler.syncWithRetry()

        assertTrue(result.isSuccess)
        assertEquals(2, slept.size, "one wait per failed attempt, and none after the one that worked")
    }

    @Test
    fun `it gives up rather than hammering a server that is not coming back`() = runBlocking {
        settings.setServerUrl("http://shop.local:8080")
        api.failures = Int.MAX_VALUE

        val result = scheduler.syncWithRetry(maxAttempts = 5)

        assertTrue(result.isFailure)
        assertEquals(4, slept.size, "five attempts means four waits between them")
    }

    @Test
    fun `a revoked device stops immediately, because waiting cannot help`() = runBlocking {
        settings.setServerUrl("http://shop.local:8080")
        api.failures = Int.MAX_VALUE
        api.failure = { SyncNotAuthorised("revoked") }

        scheduler.syncWithRetry()

        assertEquals(emptyList(), slept)
        assertTrue(platform.lines.any { "sync refused" in it })
    }

    @Test
    fun `backoff grows, stays inside its ceiling, and is jittered`() {
        val ceilings = (1..10).map { attempt ->
            (1..200).maxOf { scheduler.backoffMillis(attempt) }
        }

        // Doubling until the cap, and never past it.
        assertTrue(ceilings[0] <= 1_000)
        assertTrue(ceilings[1] <= 2_000)
        assertTrue(ceilings.all { it <= 60_000 })
        assertTrue(ceilings[5] > ceilings[0], "it has to grow, or it is not backoff")

        // Full jitter: three tills that lost the same router must not come back in lockstep.
        val sample = (1..50).map { scheduler.backoffMillis(6) }.toSet()
        assertTrue(sample.size > 10, "a fixed delay would put every till back on the wire at once")
    }
}
