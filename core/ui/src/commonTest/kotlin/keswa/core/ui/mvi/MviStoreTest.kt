package keswa.core.ui.mvi

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class CounterState(val count: Int = 0, val isLoading: Boolean = false) : MviState

private sealed interface CounterIntent : MviIntent {
    data object Increment : CounterIntent
    data object SubmitClicked : CounterIntent
    data class Internal(val result: Int) : CounterIntent
}

private sealed interface CounterEffect : MviEffect {
    data class Submitted(val result: Int) : CounterEffect
}

/** A store whose "submit" work only actually runs once, no matter how many times it's clicked. */
private class CounterStore(
    private val onSubmit: suspend (Int) -> Int,
) : MviStore<CounterState, CounterIntent, CounterEffect>(CounterState()) {

    var submitCallCount = 0
        private set

    override fun reduce(state: CounterState, intent: CounterIntent): CounterState = when (intent) {
        is CounterIntent.Increment -> state.copy(count = state.count + 1)
        is CounterIntent.SubmitClicked -> if (state.isLoading) state else state.copy(isLoading = true)
        is CounterIntent.Internal -> state.copy(isLoading = false, count = intent.result)
    }

    override suspend fun handle(intent: CounterIntent, state: CounterState) {
        when (intent) {
            is CounterIntent.SubmitClicked -> {
                if (state.isLoading) return // a prior SubmitClicked is already in flight
                submitCallCount += 1
                val result = onSubmit(state.count)
                dispatch(CounterIntent.Internal(result))
                emit(CounterEffect.Submitted(result))
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MviStoreTest {

    // viewModelScope resolves to Dispatchers.Main.immediate, same as on Android — without
    // pointing Main at this test's own scheduler, its coroutines run on a real, uncontrolled
    // dispatcher and advanceUntilIdle() has nothing to advance.
    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reduce runs in order for a burst of intents`() = runTest(testDispatcher) {
        val store = CounterStore(onSubmit = { it })
        repeat(5) { store.dispatch(CounterIntent.Increment) }
        testScheduler.advanceUntilIdle()

        assertEquals(5, store.state.value.count)
    }

    @Test
    fun `a second submit while the first is still in flight does not run the work twice`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val store = CounterStore(onSubmit = { current -> gate.await(); current + 100 })

        store.dispatch(CounterIntent.SubmitClicked)
        store.dispatch(CounterIntent.SubmitClicked) // arrives while the first is still awaiting the gate
        testScheduler.advanceUntilIdle()

        assertEquals(1, store.submitCallCount, "second click must have been ignored, not queued")
        assertTrue(store.state.value.isLoading, "still waiting on the first call's gate")

        gate.complete(Unit)
        testScheduler.advanceUntilIdle()

        assertEquals(1, store.submitCallCount, "completing the gate must not trigger a second call")
        assertEquals(100, store.state.value.count)
        assertTrue(!store.state.value.isLoading)
    }

    @Test
    fun `the first submit still proceeds even though reduce already flipped isLoading`() = runTest(testDispatcher) {
        val store = CounterStore(onSubmit = { it + 1 })
        store.dispatch(CounterIntent.SubmitClicked)
        testScheduler.advanceUntilIdle()

        assertEquals(1, store.submitCallCount, "the very first click must not be mistaken for a duplicate")
        assertEquals(1, store.state.value.count)
    }

    @Test
    fun `effects are delivered exactly once, never replayed to a late collector`() = runTest(testDispatcher) {
        val store = CounterStore(onSubmit = { it + 1 })

        store.effects.test {
            store.dispatch(CounterIntent.SubmitClicked)
            assertEquals(CounterEffect.Submitted(1), awaitItem())
        }

        // A collector that starts *after* the effect already fired must see nothing —
        // Channel-backed effects have no replay, unlike a SharedFlow(replay = 1).
        store.effects.test {
            expectNoEvents()
        }
    }

    @Test
    fun `a slow handle does not block a later intent's reduction`() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val store = CounterStore(onSubmit = { current -> gate.await(); current })

        store.dispatch(CounterIntent.SubmitClicked) // handle() blocks on the gate
        store.dispatch(CounterIntent.Increment)      // must still reduce immediately
        testScheduler.advanceUntilIdle()

        assertEquals(1, store.state.value.count, "Increment's reduce must not wait on Submit's handle")
        gate.complete(Unit)
    }
}
