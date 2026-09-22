package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.sync.LogApplier
import com.alsoug.keswa.core.sync.LoggedRow
import com.alsoug.keswa.core.sync.SyncEngine
import com.alsoug.keswa.core.sync.SyncNotAuthorised
import com.alsoug.keswa.core.sync.SyncOutcome
import com.alsoug.keswa.core.sync.SyncSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The engine's own bookkeeping: what it sends, what it keeps, and where it stops.
 */
class SyncEngineTest {

    private val database = createTestDatabase()
    private val api = FakeSyncApi()
    private val tokens = FakeTokenStore()
    private val settings = SyncSettings(database.settingDao(), UuidIdGenerator())
    private val engine = SyncEngine(
        database = database,
        api = api,
        applier = LogApplier(database, UuidIdGenerator(), { CLOCK }),
        settings = settings,
        tokens = tokens,
        platform = FakePlatform(),
    )

    @AfterTest
    fun tearDown() = database.close()

    private suspend fun configured() {
        settings.setServerUrl("http://shop.local:8080")
        tokens.store("a-token")
    }

    @Test
    fun `a shop with no server is not an error`() = runBlocking {
        database.seedBaseData()

        // The normal state of a shop with one till. Reporting it as a failure would fill the log
        // with something nobody needs to act on.
        assertIs<SyncOutcome.NotConfigured>(engine.syncOnce().getOrThrow())
        Unit
    }

    @Test
    fun `configured but not enrolled says so rather than failing`() = runBlocking {
        settings.setServerUrl("http://shop.local:8080")

        assertIs<SyncOutcome.NotEnrolled>(engine.syncOnce().getOrThrow())
        Unit
    }

    @Test
    fun `enrolling stores the token and the block`() = runBlocking {
        engine.enrol("http://shop.local:8080", "ABCD1234").getOrThrow()

        assertEquals("token-for-${settings.deviceId()}", tokens.read())
        assertEquals(1, settings.ordinal())
        assertEquals(1_000_000L..1_999_999L, settings.receiptBlock())
    }

    @Test
    fun `a push sends the outbox and drains it`() = runBlocking {
        configured()
        database.seedBaseData()
        val queued = database.syncDao().pendingCount()
        assertTrue(queued > 0)

        val outcome = assertIs<SyncOutcome.Completed>(engine.syncOnce().getOrThrow())

        assertEquals(queued.toInt(), outcome.pushed)
        assertEquals(0, database.syncDao().pendingCount())
    }

    @Test
    fun `a row edited five times is sent once`() = runBlocking {
        configured()
        database.seedBaseData()
        engine.syncOnce().getOrThrow()
        repeat(5) { index ->
            database.colourDao().upsert(
                com.alsoug.keswa.core.database.entities.ColourEntity(
                    COLOUR_NAVY, "Shade $index", "ظل", "#10182$index", 0, isActive = true,
                ),
            )
        }

        engine.syncOnce().getOrThrow()

        val sent = api.pushed.last().filter { it.table == "colour" }
        assertEquals(1, sent.size)
        assertEquals("Shade 4", sent.single().columns.getValue("name").value)
    }

    @Test
    fun `a row changed while the push was in flight is not drained with it`() = runBlocking {
        configured()
        database.seedBaseData()
        val inFlight = database.syncDao().pending(limit = 1_000).last().seq

        // Something is written after the batch was read, as happens on any busy till.
        database.stockLedgerDao().insertMovement(receipt(quantity = 7))
        database.syncDao().drainThrough(inFlight)

        assertEquals(
            listOf("stock_movement"),
            database.syncDao().pending(limit = 10).map { it.tableName },
        )
    }

    @Test
    fun `the cursor advances only over what was applied`() = runBlocking {
        configured()
        database.seedBaseData()
        engine.syncOnce().getOrThrow()
        val settled = database.syncDao().cursor()

        // A movement for a variant this device has never heard of cannot be applied yet.
        api.log += LoggedRow(
            seq = api.log.size + 1L,
            deviceId = "other-device",
            row = com.alsoug.keswa.core.sync.SyncRow(
                table = "stock_movement",
                id = "mov-orphan",
                columns = orphanMovementColumns(),
            ),
        )

        val outcome = assertIs<SyncOutcome.Completed>(engine.syncOnce().getOrThrow())

        assertEquals(1, outcome.deferred)
        assertEquals(settled, database.syncDao().cursor(), "the cursor must not step over a deferred row")
    }

    @Test
    fun `a refused device keeps its work`() = runBlocking {
        configured()
        database.seedBaseData()
        api.failures = 1
        api.failure = { SyncNotAuthorised("revoked") }
        val queued = database.syncDao().pendingCount()

        val result = engine.syncOnce()

        assertTrue(result.isFailure)
        assertIs<SyncNotAuthorised>(result.exceptionOrNull())
        // Nothing this device did is lost while somebody sorts out the enrolment.
        assertEquals(queued, database.syncDao().pendingCount())
    }

    @Test
    fun `a backlog drains in batches and says there is more`() = runBlocking {
        configured()
        database.seedBaseData()
        repeat(12) { index ->
            database.stockLedgerDao().insertMovement(
                receipt(quantity = 1, at = CLOCK + index).copy(id = "mov-$index"),
            )
        }

        val first = assertIs<SyncOutcome.Completed>(engine.syncOnce(batchSize = 5).getOrThrow())

        assertEquals(5, first.pushed)
        assertTrue(first.hasMore)
        assertTrue(database.syncDao().pendingCount() > 0)

        repeat(10) { engine.syncOnce(batchSize = 5) }
        assertEquals(0, database.syncDao().pendingCount())
    }

    private companion object {
        const val CLOCK = 1_757_000_000_000L
    }
}
