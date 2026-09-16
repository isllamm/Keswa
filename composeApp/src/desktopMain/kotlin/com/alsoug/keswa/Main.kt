package com.alsoug.keswa

import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.LogLevel
import com.alsoug.keswa.di.APPLICATION_SCOPE
import com.alsoug.keswa.di.initKoin
import com.alsoug.keswa.features.catalog.domain.usecase.SeedShopUseCase
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.qualifier.named

fun main() {
    // Koin 4 wires the Compose context from startKoin itself — no KoinContext wrapper needed.
    val koin = initKoin().koin

    warmUpDatabase(
        database = koin.get(),
        seed = koin.get(),
        scope = koin.get(named(APPLICATION_SCOPE)),
        platform = koin.get(),
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Keswa",
        ) {
            App()
        }
    }
}

/**
 * Forces the first connection so the schema is created and any migration runs before a screen
 * needs data.
 *
 * Resolving [KeswaDatabase] from the graph is not enough on its own — Room builds the object
 * eagerly but opens the connection lazily, so nothing reaches disk until a DAO is called.
 *
 * Deliberately off the main thread and not awaited: `runBlocking` is a blocker
 * (CODE_GUIDELINES, forbidden patterns), and a migration on a large ledger should never freeze the
 * window. A failure is logged here; surfacing it to the user belongs with the first real screen in
 * Phase 2.
 */
private fun warmUpDatabase(
    database: KeswaDatabase,
    seed: SeedShopUseCase,
    scope: CoroutineScope,
    platform: IPlatformProvider,
) {
    scope.launch {
        runCatching {
            database.locationDao().getDefault()
            // Idempotent, and the first sale depends on it: a fresh install has no colours, no
            // location and no price list, and none of those can be created from a screen.
            seed().getOrThrow()
        }
            .onSuccess { platform.log(LogLevel.INFO, TAG, "database ready") }
            .onFailure { platform.log(LogLevel.ERROR, TAG, "database failed to open", it) }
    }
}

private const val TAG = "Startup"
