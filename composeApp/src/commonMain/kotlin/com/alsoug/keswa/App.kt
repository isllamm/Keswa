package com.alsoug.keswa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardScreen
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInScreen
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserScreen
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorScreen
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustScreen
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountScreen
import com.alsoug.keswa.features.inventory.presentation.screens.importer.ImportScreen
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingScreen
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsScreen
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftScreen
import com.alsoug.keswa.features.sell.presentation.screens.till.TillScreen
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsScreen
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomersScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Routes for the app shell.
 *
 * Features emit their own navigation results and know nothing about these — the shell does the
 * mapping, which is what keeps `features:A` from ever importing `features:B`.
 */
internal sealed interface Route {
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
    data object Returns : Route
    data object Dashboard : Route
    data object Customers : Route
}

@Composable
fun App(sessions: ISessionManager = koinInject()) {
    val session by sessions.current.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message -> scope.launch { snackbars.showSnackbar(message) } }

    KeswaTheme {
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

    Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            // The handheld from Phase 6 is the reason this is measured rather than assumed.
            val compact = maxWidth < COMPACT_WIDTH

            if (compact) {
                Column(Modifier.fillMaxSize()) {
                    NavigationStrip(current = route, onNavigate = { route = it })
                    Destination(route, notify, onNavigate = { route = it }, Modifier.weight(1f))
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    NavigationSidebar(
                        current = route,
                        operator = operator,
                        role = role,
                        onNavigate = { route = it },
                        onSignOut = onSignOut,
                    )
                    Destination(route, notify, onNavigate = { route = it }, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Maps a route to its screen.
 *
 * Features emit their own navigation results and know nothing about these — the shell does the
 * mapping, which is what keeps `features:A` from ever importing `features:B`.
 */
@Composable
private fun Destination(
    route: Route,
    notify: (String) -> Unit,
    onNavigate: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val current = route) {
        is Route.Till -> TillScreen(
            viewModel = koinInject(),
            onMessage = notify,
            modifier = modifier,
        )

        is Route.ShiftClose -> ShiftScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Till) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Customers -> CustomersScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Till) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Dashboard -> DashboardScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Till) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Returns -> ReturnsScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Till) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Stockroom -> StockroomHub(
            onOpen = onNavigate,
            onSettings = { onNavigate(Route.Settings) },
            modifier = modifier,
        )

        is Route.Receiving -> ReceivingScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Stockroom) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.StockCount -> CountScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Stockroom) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Adjust -> AdjustScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Stockroom) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Import -> ImportScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Stockroom) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Catalogue -> CatalogBrowserScreen(
            viewModel = koinInject(),
            onOpenProduct = { onNavigate(Route.ProductEditor(it)) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.ProductEditor -> ProductEditorScreen(
            productId = current.productId,
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Catalogue) },
            onMessage = notify,
            modifier = modifier,
        )

        is Route.Settings -> SettingsScreen(
            viewModel = koinInject(),
            onBack = { onNavigate(Route.Stockroom) },
            onMessage = notify,
            modifier = modifier,
        )
    }
}

/**
 * The four things somebody does in a stockroom.
 *
 * A hub rather than four more entries in the sidebar. It also keeps the feature modules ignorant
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

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Stockroom", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Everything that is not selling",
            style = MaterialTheme.typography.bodySmall,
            color = KeswaTheme.semantics.muted,
        )

        entries.forEach { (route, title, subtitle) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(top = 10.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
                    .border(1.dp, KeswaTheme.semantics.grid, RoundedCornerShape(7.dp))
                    .clickable { onOpen(route) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = KeswaTheme.semantics.muted,
                    )
                }
                // A chevron, not a button: the whole row is the target, and two hit areas in one
                // row is how somebody taps the wrong one.
                Text("›", style = MaterialTheme.typography.titleLarge, color = KeswaTheme.semantics.axis)
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Printers and scanner",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onSettings),
        )
    }
}
