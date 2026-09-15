package com.alsoug.keswa

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInScreen
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserScreen
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorScreen
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsScreen
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
    data object Settings : Route
}

@Composable
fun App(sessions: ISessionManager = koinInject()) {
    val session by sessions.current.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbars.showSnackbar(message) } }

    MaterialTheme {
        // The whole app sits behind a session. Screens still check their own permissions in the
        // domain layer — this gate is convenience, not the security boundary.
        if (session == null) {
            Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
                SignInScreen(
                    viewModel = koinInject(),
                    onSignedIn = { },
                    onMessage = notify,
                    modifier = Modifier.padding(padding),
                )
            }
        } else {
            SignedInApp(
                operator = session!!.user.displayName,
                role = session!!.role.name.lowercase(),
                snackbars = snackbars,
                notify = notify,
                onSignOut = sessions::signOut,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignedInApp(
    operator: String,
    role: String,
    snackbars: SnackbarHostState,
    notify: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var route: Route by remember { mutableStateOf<Route>(Route.Catalogue) }

    Scaffold(
        topBar = {
            // Who is operating the till stays visible: every sale is attributed to them, and on a
            // shared machine that is worth not having to remember.
            TopAppBar(
                title = { Text("Keswa") },
                actions = {
                    Text(
                        "$operator · $role",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        floatingActionButton = {
            if (route is Route.Catalogue) {
                ExtendedFloatingActionButton(
                    onClick = { route = Route.Settings },
                    text = { Text("Printers") },
                    icon = {},
                )
            }
        },
    ) { padding ->
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

            is Route.Settings -> SettingsScreen(
                viewModel = koinInject(),
                onBack = { route = Route.Catalogue },
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
