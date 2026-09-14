package com.alsoug.keswa.core.error

import com.alsoug.keswa.core.coroutines.runCatchingCancellable

/**
 * Wraps a suspend call in a [Result], catching thrown exceptions.
 *
 * Usage:
 * ```kotlin
 * suspend fun loadProducts(): Result<List<Product>> = safeApiCall {
 *     productDao.getAll()
 * }
 * ```
 *
 * Delegates to [runCatchingCancellable] rather than a bare `catch (e: Exception)`, so a
 * [kotlinx.coroutines.CancellationException] is rethrown instead of being converted into a
 * `Result.failure`. Swallowing it breaks structured concurrency — the coroutine keeps running
 * after its scope is canceled.
 */
suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): Result<T> =
    runCatchingCancellable { block() }
