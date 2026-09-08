package keswa.app.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import keswa.core.ui.theme.KeswaTheme
import keswa.domain.Principal
import keswa.feature.auth.PinRoute
import keswa.feature.catalog.CatalogRoute
import org.koin.core.context.startKoin
import java.util.Locale

private sealed interface Screen {
    data object Pin : Screen
    data class Home(val principal: Principal) : Screen
    data class Catalog(val principal: Principal) : Screen
}

fun main() {
    Locale.setDefault(Locale("ar")) // Arabic-first — see docs/architecture.md §6

    startKoin { modules(appModule) }
    bootstrap()

    application {
        Window(onCloseRequest = ::exitApplication, title = "Keswa") {
            KeswaTheme {
                KeswaApp()
            }
        }
    }
}

/** First-run bootstrap: create/open the database and seed it if this is a fresh install. */
private fun bootstrap() {
    val koin = org.koin.core.context.GlobalContext.get()
    val deviceId = koin.get<keswa.core.common.DeviceIdProvider>().current()
    koin.get<keswa.data.Seeder>().seedIfEmpty(deviceId.value)
}

@Composable
private fun KeswaApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Pin) }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = screen) {
            is Screen.Pin -> PinRoute(onUnlocked = { principal -> screen = Screen.Home(principal) })
            is Screen.Home -> Home(current.principal, onOpenCatalog = { screen = Screen.Catalog(current.principal) })
            is Screen.Catalog -> Column(Modifier.fillMaxSize()) {
                Button(onClick = { screen = Screen.Home(current.principal) }, modifier = Modifier.padding(8.dp)) {
                    Text("← Home")
                }
                CatalogRoute()
            }
        }
    }
}

@Composable
private fun Home(principal: Principal, onOpenCatalog: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Keswa", style = MaterialTheme.typography.headlineMedium)
        Text("Unlocked as ${principal.roleCode} (user ${principal.userId})")
        Button(onClick = onOpenCatalog) { Text("Products") }
        Text("POS and the rest of Phase 0 land in a follow-up session.")
    }
}
