package com.alsoug.keswa.core.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Provides coroutine dispatchers as injectable dependencies.
 *
 * Following the Android Architecture Samples pattern:
 * https://github.com/android/architecture-samples
 *
 * Inject this instead of bare [CoroutineDispatcher] so that:
 * - Koin can resolve it without qualifiers
 * - Tests can substitute [TestDispatcherProvider] for full coroutine control
 */
interface DispatcherProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
}

/**
 * Production implementation — delegates to platform [Dispatchers].
 */
class DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val default: CoroutineDispatcher = Dispatchers.Default
}
