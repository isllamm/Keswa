package com.alsoug.keswa.core.database

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Covers the desktop half of persistence: the production builder, at a real path, creating the
 * real schema.
 *
 * Worth its own test because the failure it guards against is silent. Resolving [KeswaDatabase]
 * from the DI graph constructs Room's wrapper but opens no connection — nothing reaches disk until
 * a DAO is called, so "the database is wired" and "the database exists" are different claims.
 */
class DesktopDatabaseTest {

    private val directory: File = Files.createTempDirectory("keswa-desktop").toFile()

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `the production builder creates the schema on first use`() = runTest {
        val database = getKeswaDatabase(getDatabaseBuilder(directory), RealDispatchers)
        val file = directory.resolve("keswa.db")

        // Constructing it is not enough — Room opens lazily.
        assertTrue(!file.exists() || file.length() == 0L, "no connection should be open yet")

        // When the first query runs
        database.seedBaseData()

        // Then the file exists and carries the whole schema
        assertTrue(file.exists() && file.length() > 0, "expected a database at ${file.absolutePath}")
        assertEquals(1, database.locationDao().getAll().size)
        assertEquals("Downtown", database.locationDao().getDefault()?.name)
        database.close()
    }

    @Test
    fun `the app data directory resolves to the platform convention`() {
        val path = appDataDirectory().absolutePath
        val os = System.getProperty("os.name").orEmpty().lowercase()

        val expected = when {
            os.contains("mac") -> "Library/Application Support/Keswa"
            os.contains("win") -> "Keswa"
            else -> "Keswa"
        }
        assertTrue(path.endsWith(expected), "unexpected data directory: $path")
        assertTrue(appDataDirectory().isDirectory, "the directory should be created on resolve")
    }
}
