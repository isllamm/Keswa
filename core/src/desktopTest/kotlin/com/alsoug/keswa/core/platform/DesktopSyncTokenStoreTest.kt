package com.alsoug.keswa.core.platform

import com.alsoug.keswa.core.coroutines.RealDispatchers
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * KD-007 on desktop: a file only this user account can read.
 *
 * The ADR is explicit that this is weaker than a keystore and why it is still the right trade —
 * `keswa.db` sits in the same directory holding the shop's entire trading history in plaintext.
 * What is tested here is that the weaker thing is at least done properly.
 */
class DesktopSyncTokenStoreTest {

    private val directory: File = Files.createTempDirectory("keswa-token").toFile()
    private val file = File(directory, "sync-token")
    private val store = DesktopSyncTokenStore(RealDispatchers, file)

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `a token round-trips`() = runBlocking {
        store.store("a-device-token")

        assertEquals("a-device-token", store.read())
    }

    @Test
    fun `nothing stored reads as nothing, not as an empty token`() = runBlocking {
        assertNull(store.read())
    }

    @Test
    fun `clearing it leaves nothing behind`() = runBlocking {
        store.store("a-device-token")

        store.clear()

        assertNull(store.read())
        assertTrue(!file.exists())
    }

    @Test
    fun `only the owner can read it`() = runBlocking {
        store.store("a-device-token")

        val permissions = Files.getPosixFilePermissions(file.toPath())

        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            permissions,
        )
    }
}
