package com.alsoug.keswa.core.coroutines

/**
 * Reports non-timeout coroutine cancellation rethrows.
 *
 * This is invoked right before rethrowing a [kotlinx.coroutines.CancellationException]
 * (excluding [kotlinx.coroutines.TimeoutCancellationException]).
 *
 * Implementations live outside :core (e.g. composeApp) and may forward to:
 * - platform logger
 * - analytics EventTracker
 * - crash reporting breadcrumbs / non-fatals
 */
interface CancellationRethrowReporter {
    fun onCancellationRethrow(t: Throwable)
}

/**
 * Global hook used by [rethrowIfCancellation] to notify app-level loggers.
 *
 * Set this once in the application composition root after DI is initialized.
 */
object CancellationRethrowReporterHolder {
    var reporter: CancellationRethrowReporter? = null
}

