package keswa.feature.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CatalogRoute() {
    val store: CatalogStore = koinViewModel()
    val state by store.state.collectAsState()

    LaunchedEffect(Unit) {
        store.dispatch(CatalogContract.Intent.ScreenEntered)
    }

    LaunchedEffect(store) {
        store.effects.collect { /* no navigation yet — ProductSaved just returns to the list */ }
    }

    CatalogScreen(state = state, onIntent = store::dispatch)
}
