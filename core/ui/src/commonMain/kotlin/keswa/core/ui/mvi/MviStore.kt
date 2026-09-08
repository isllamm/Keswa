package keswa.core.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base for every screen's MVI store. See docs/presentation-architecture.md for the full
 * contract; the short version:
 *
 * - [reduce] is pure and synchronous — no I/O, no clock, no coroutines. Every state transition
 *   is a plain function, testable with zero infrastructure.
 * - [handle] does the actual work (use cases, repositories) and feeds results back through
 *   [dispatch] as an `Internal` intent. A use case result never writes state directly.
 * - Effects are one-shot: delivered over a [Channel], never replayed.
 *
 * Intents are also queued on a [Channel], not a `SharedFlow` — a `SharedFlow` with no replay only
 * delivers to whichever collector is *already* actively suspended in `collect` at the moment of
 * emission; anything dispatched before that collector has started running is silently dropped.
 * Since a store's collector is launched asynchronously in [init] (queued on `viewModelScope`, not
 * guaranteed to be running yet), an intent dispatched right after construction could vanish. A
 * `Channel` is the right primitive for a point-to-point work queue: a value sent is reliably
 * buffered until *some* consumer reads it, whenever that ends up happening.
 *
 * Reductions are strictly ordered (one intent reduces at a time, in arrival order); [handle] runs
 * concurrently so a slow use case can never stall an intent arriving behind it.
 *
 * The `state` [handle] receives is deliberately the state **as of just before this intent's own
 * reduction** — everything earlier intents already committed, but not what this intent's own
 * [reduce] call just changed. This is what makes a double-submit guard work: give [reduce] an
 * idempotent transition (`if (state.isLoading) state else state.copy(isLoading = true)`) and have
 * [handle] check the *same* flag on the state it was handed — a second, near-simultaneous dispatch
 * sees `isLoading` already `true` (a wiser/earlier intent set it) and skips, while the first
 * dispatch still sees it `false` and proceeds. Passing the post-reduce state instead would make
 * every dispatch — including the first — see its own just-set flag and bail immediately.
 */
abstract class MviStore<S : MviState, I : MviIntent, E : MviEffect>(
    initialState: S,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    private val intents = Channel<I>(capacity = 64)

    init {
        viewModelScope.launch {
            for (intent in intents) {
                val stateBeforeThisIntent = _state.value
                _state.update { current -> reduce(current, intent) }
                viewModelScope.launch { handle(intent, stateBeforeThisIntent) }
            }
        }
    }

    fun dispatch(intent: I) {
        val result = intents.trySend(intent)
        check(result.isSuccess) { "Intent buffer full — dropped $intent. See MviStore's ordering guarantees." }
    }

    protected abstract fun reduce(state: S, intent: I): S

    /**
     * @param state the state just before this intent was reduced — see the class doc's note on
     *   why this (not the post-reduce state) is what makes a double-submit guard actually work.
     */
    protected open suspend fun handle(intent: I, state: S) {}

    protected fun emit(effect: E) {
        _effects.trySend(effect)
    }
}
