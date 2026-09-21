package com.alsoug.keswa.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers that actually run work.
 *
 * Room needs one: virtual time would stall its driver, so anything touching the database uses this
 * rather than [TestDispatcherProvider].
 */
object RealDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Default
    override val main: CoroutineDispatcher = Dispatchers.Default
    override val default: CoroutineDispatcher = Dispatchers.Default
}
