package keswa.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import keswa.domain.Principal
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PinRoute(onUnlocked: (Principal) -> Unit) {
    val store: PinStore = koinViewModel()
    val state by store.state.collectAsState()

    LaunchedEffect(Unit) {
        store.dispatch(PinContract.Intent.ScreenEntered)
    }

    LaunchedEffect(store) {
        store.effects.collect { effect ->
            when (effect) {
                is PinContract.Effect.Unlocked -> onUnlocked(effect.principal)
            }
        }
    }

    PinScreen(state = state, onIntent = store::dispatch)
}
