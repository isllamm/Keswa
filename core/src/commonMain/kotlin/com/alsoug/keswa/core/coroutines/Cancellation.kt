package com.alsoug.keswa.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/**
 * Coroutine cancellation is control-flow, not an application error.
 *
 * Best practice: never swallow or wrap [CancellationException]; always rethrow so structured
 * concurrency can cancel children and clean up correctly.
 */
fun Throwable.rethrowIfCancellation(): Nothing? {
    // Timeouts use TimeoutCancellationException (a CancellationException subtype) but are
    // typically handled as a real failure (e.g., show network error), not propagated as
    // "normal cancellation" of the coroutine scope.
    if (this is CancellationException && this !is TimeoutCancellationException) {
        CancellationRethrowReporterHolder.reporter?.onCancellationRethrow(this)
        throw this
    }
    return null
}

/**
 * Like Kotlin's [runCatching], but **never** turns [CancellationException] into a [Result.failure].
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (t: Throwable) {
        t.rethrowIfCancellation()
        Result.failure(t)
    }

