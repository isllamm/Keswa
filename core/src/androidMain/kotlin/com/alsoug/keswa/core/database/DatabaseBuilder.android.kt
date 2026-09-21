package com.alsoug.keswa.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Builds the database where Android keeps app databases.
 *
 * Not `expect`/`actual` but a same-named function per source set — the pattern `kmp_cashimobile`
 * uses, and necessarily so: this one needs a [Context] and the desktop one needs a directory, so
 * the signatures cannot be made to agree.
 *
 * `Context.getDatabasePath` rather than a path of our own: it puts the file in the app's private
 * storage, where a backup picks it up and another app cannot read it.
 */
fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<KeswaDatabase> {
    val file = context.getDatabasePath(DATABASE_NAME)
    return Room.databaseBuilder<KeswaDatabase>(context, file.absolutePath)
}

private const val DATABASE_NAME = "keswa.db"
