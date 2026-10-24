package com.alsoug.keswa.core.database

import androidx.room.useWriterConnection

/** The triggers SQLite actually holds, as opposed to the ones the code meant to install. */
suspend fun KeswaDatabase.triggerNames(): Set<String> = useWriterConnection { transactor ->
    transactor.usePrepared("SELECT name FROM sqlite_master WHERE type = 'trigger'") { statement ->
        buildSet {
            while (statement.step()) add(statement.getText(0))
        }
    }
}
