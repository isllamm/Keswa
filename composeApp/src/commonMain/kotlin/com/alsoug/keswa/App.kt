package com.alsoug.keswa

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
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
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustScreen
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountScreen
import com.alsoug.keswa.features.inventory.presentation.screens.importer.ImportScreen
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingScreen
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftScreen
import com.alsoug.keswa.features.sell.presentation.screens.till.TillScreen
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
    data object Till : Route
    data object Catalogue : Route
    data class ProductEditor(val productId: String) : Route
    data object Settings : Route
    data object ShiftClose : Route
    data object Stockroom : Route
    data object Receiving : Route
    data object StockCount : Route
    data object Adjust : Route
    data object Import : Route
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
    // The till is where a shop spends its day, so it is what opens — the catalogue is the
    // back-office errand, not the other way round.
    var route: Route by remember { mutableStateOf<Route>(Route.Till) }

    Scaffold(
        topBar = {
            // Who is operating the till stays visible: every sale is attributed to them, and on a
            // shared machine that is worth not having to remember.
            TopAppBar(
                title = { Text("Keswa") },
                actions = {
                    // Four destinations, not eight: the stockroom's own jobs sit behind one
                    // entry, which is the only way this bar still fits on a handheld.
                    TextButton(onClick = { route = Route.Till }) { Text("Till") }
                    TextButton(onClick = { route = Route.Stockroom }) { Text("Stockroom") }
                    TextButton(onClick = { route = Route.Catalogue }) { Text("Catalogue") }
                    TextButton(onClick = { route = Route.ShiftClose }) { Text("Shift") }
                    Text(
                        "$operator · $role",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                    )
                    TextButton(onClick = onSignOut) { Text("Sign out") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        when (val current = route) {
            is Route.Till -> TillScreen(
                viewModel = koinInject(),
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )

            is Route.ShiftClose -> ShiftScreen(
                viewModel = koinInject(),
                onBack = { route = Route.Till },
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )

            is Route.Stockroom -> StockroomHub(
                onOpen = { route = it },
                onSettings = { route = Route.Settings },
                modifier = Modifier.padding(padding),
            )

            is Route.Receiving -> ReceivingScreen(
                viewModel = koinInject(),
                onBack = { route = Route.Stockroom },
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )

            is Route.StockCount -> CountScreen(
                viewModel = koinInject(),
                onBack = { route = Route.Stockroom },
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )

            is Route.Adjust -> AdjustScreen(
                viewModel = koinInject(),
                onBack = { route = Route.Stockroom },
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )

            is Route.Import -> ImportScreen(
                viewModel = koinInject(),
                onBack = { route = Route.Stockroom },
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )

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
                onBack = { route = Route.Stockroom },
                onMessage = notify,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/**
 * The four things somebody does in a stockroom.
 *
 * A hub rather than four more entries in the top bar. It also keeps the feature modules ignorant
 * of one another: each screen reports that it is finished, and the shell decides where that leads.
 */
@Composable
private fun StockroomHub(
    onOpen: (Route) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = listOf(
        Triple(Route.Receiving, "Receiving", "Book in a delivery, and let it set the cost"),
        Triple(Route.StockCount, "Stock count", "Blind — the expected figure comes after"),
        Triple(Route.Adjust, "Adjust", "Damage, loss, anything a document cannot explain"),
        Triple(Route.Import, "Import catalogue", "A supplier's spreadsheet, validated as a whole"),
    )

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Stockroom", style = MaterialTheme.typography.titleLarge)

        entries.forEach { (route, title, subtitle) ->
            Card(
                onClick = { onOpen(route) },
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(top = 12.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        TextButton(onClick = onSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text("Printers and scanner")
        }
    }
}
