package com.alsoug.keswa.core.coroutines

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * Test [DispatcherProvider] backed by a single [StandardTestDispatcher], so every dispatcher
 * shares one scheduler and `runTest` controls virtual time across all of them.
 *
 * KD-003: production code injects [DispatcherProvider] rather than referencing [kotlinx.coroutines.Dispatchers]
 * directly, precisely so this substitution is possible. `kmp_cashimobile` declares the same intent in
 * `DispatcherProvider`'s KDoc but never created this type — which is why hardcoded dispatchers
 * persisted there (and became defect T2 in the cashi_pax request-money review).
 */
class TestDispatcherProvider(
    scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    private val dispatcher: CoroutineDispatcher = StandardTestDispatcher(scheduler),
) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher

    val context: CoroutineContext get() = dispatcher
}
