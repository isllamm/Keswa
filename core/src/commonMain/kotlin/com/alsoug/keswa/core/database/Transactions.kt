package com.alsoug.keswa.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Runs [block] as one database transaction, across as many DAOs as it needs.
 *
 * Room's own `@Transaction` is scoped to a single DAO, and a sale is not: it writes a header, its
 * lines, its payments and its stock movements, and those either all happen or none of them do. A
 * sale that committed half-way is a customer holding a receipt for stock the shop still thinks it
 * has.
 *
 * Room confines the writer connection to the calling coroutine, so ordinary DAO calls inside
 * [block] join this transaction rather than opening their own. Anything thrown rolls the whole
 * thing back.
 *
 * `IMMEDIATE` rather than the default deferred: the write lock is taken up front, so two tills
 * sharing a database fail fast instead of half-way through.
 */
suspend fun <R> KeswaDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { transactor -> transactor.immediateTransaction { block() } }
