package com.alsoug.keswa.server

import com.alsoug.keswa.core.sync.LogApplier

/**
 * Turns the log into a shop.
 *
 * The log is the durable artefact; these tables are derived from it and can be thrown away and
 * rebuilt, which is the whole of 9c's exit path and is exercised by a test rather than promised.
 *
 * `respectLocalEdits = false`: the server has no local edits to protect. Nobody sells on it.
 */
class Materialiser(
    private val log: LogStore,
    private val applier: LogApplier,
) {

    data class Result(val applied: Int, val deferred: Int, val through: Long)

    suspend fun run(batch: Int = BATCH): Result {
        val from = log.materialisedThrough()
        val rows = log.read(since = from, limit = batch)
        if (rows.isEmpty()) return Result(0, 0, from)

        val outcome = applier.apply(rows, respectLocalEdits = false)

        // Stop before the earliest row that is still waiting on a parent, exactly as the till does.
        // A sale header whose lines are in the next batch is durable in the log either way; it just
        // is not a shop yet.
        val through = outcome.deferred.minOfOrNull { it.seq - 1 } ?: rows.last().seq
        if (through > from) log.setMaterialisedThrough(through)

        return Result(outcome.applied, outcome.deferred.size, through)
    }

    /** Drains as far as the log goes, which is what a rebuild and a large first sync both need. */
    suspend fun runToCompletion(batch: Int = BATCH): Result {
        var total = 0
        var last = Result(0, 0, log.materialisedThrough())
        while (true) {
            val result = run(batch)
            total += result.applied
            if (result.applied == 0 || result.through <= last.through) {
                return Result(total, result.deferred, result.through)
            }
            last = result
        }
    }

    private companion object {
        const val BATCH = 500
    }
}
