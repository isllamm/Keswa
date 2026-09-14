package com.alsoug.keswa

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserScreen
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Routes for the app shell.
 *
 * Features emit their own navigation results and know nothing about these — the shell does the
 * mapping, which is what keeps `features:A` from ever importing `features:B`.
 */
private sealed interface Route {
    data object Catalogue : Route
    data class ProductEditor(val productId: String) : Route
}

@Composable
fun App() {
    var route: Route by remember { mutableStateOf<Route>(Route.Catalogue) }
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbars.showSnackbar(message) } }

    MaterialTheme {
        Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
            when (val current = route) {
                is Route.Catalogue -> CatalogBrowserScreen(
                    viewModel = koinInject(),
                    onOpenProduct = { route = Route.ProductEditor(it) },
                    onMessage = notify,
                    modifier = Modifier.padding(padding),
                )

                is Route.ProductEditor -> ProductEditorScreen(
                    productId = current.productId,
                    viewModel = koinInject(),
                    onBack = { route = Route.Catalogue },
                    onMessage = notify,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
