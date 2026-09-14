package com.alsoug.keswa.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/**
 * Builds the database at the platform's conventional application-data location.
 *
 * Not `expect`/`actual` but a same-named function per source set — the pattern `kmp_cashimobile`
 * uses for `getDatabaseBuilder()`, where the Android and iOS signatures deliberately differ.
 *
 * The path is a real decision, not boilerplate: `Context.getDatabasePath()` has no desktop
 * analogue, and putting a shop's only copy of its sales ledger somewhere the OS may clear — or
 * somewhere that does not survive an upgrade — is a data-loss bug.
 */
fun getDatabaseBuilder(
    directory: File = appDataDirectory(),
): RoomDatabase.Builder<KeswaDatabase> {
    val file = directory.resolve(DATABASE_NAME)
    return Room.databaseBuilder<KeswaDatabase>(name = file.absolutePath)
}

/** Resolved once at startup and logged, so support can find the file. */
fun appDataDirectory(): File {
    val home = File(System.getProperty("user.home").orEmpty())
    val os = System.getProperty("os.name").orEmpty().lowercase()

    val base = when {
        os.contains("mac") -> home.resolve("Library/Application Support")
        os.contains("win") ->
            System.getenv("APPDATA")?.let(::File) ?: home.resolve("AppData/Roaming")
        else ->
            System.getenv("XDG_DATA_HOME")?.let(::File) ?: home.resolve(".local/share")
    }

    return base.resolve(APP_DIRECTORY).also { it.mkdirs() }
}

private const val APP_DIRECTORY = "Keswa"
private const val DATABASE_NAME = "keswa.db"
