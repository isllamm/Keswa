package com.alsoug.keswa.core.database

import com.alsoug.keswa.core.data.repository.SyncRepositoryImpl
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.sync.LogApplier
import com.alsoug.keswa.core.sync.SyncEngine
import com.alsoug.keswa.core.sync.SyncSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SyncRepositoryTest {

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
    private val repository = SyncRepositoryImpl(engine, settings)

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun `getStatus returns default unconfigured state initially`() = runBlocking {
        val status = repository.getStatus().getOrThrow()

        assertTrue(status.deviceId.isNotEmpty())
        assertEquals("Till", status.deviceName)
        assertEquals(null, status.serverUrl)
        assertEquals(0, status.ordinal)
        assertFalse(status.isEnrolled)
        assertEquals("#0 – #999999", status.receiptRange)
    }

    @Test
    fun `enrol registers device and updates sync status`() = runBlocking {
        val outcome = repository.enrol(
            serverUrl = "http://shop.local:8080/",
            deviceName = "Main Counter",
            code = "CODE1234",
        )

        assertTrue(outcome.isSuccess)
        val status = repository.getStatus().getOrThrow()
        assertEquals("http://shop.local:8080", status.serverUrl)
        assertEquals("Main Counter", status.deviceName)
        assertEquals(1, status.ordinal)
        assertTrue(status.isEnrolled)
        assertEquals("#1000000 – #1999999", status.receiptRange)
    }

    @Test
    fun `syncNow reflects unconfigured and completed sync outcomes`() = runBlocking {
        // Unconfigured
        val unconfigured = repository.syncNow().getOrThrow()
        assertFalse(unconfigured.isConfigured)
        assertFalse(unconfigured.isEnrolled)

        // Enrolled
        repository.enrol("http://shop.local:8080", "Main Counter", "CODE1234")
        val completed = repository.syncNow().getOrThrow()
        assertTrue(completed.isConfigured)
        assertTrue(completed.isEnrolled)
        assertEquals(0, completed.pushed)
        assertEquals(0, completed.pulled)
    }

    private companion object {
        const val CLOCK = 1_757_000_000_000L
    }
}
