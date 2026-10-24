package com.alsoug.keswa

import android.app.Application
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.LogLevel
import com.alsoug.keswa.di.APPLICATION_SCOPE
import com.alsoug.keswa.di.initKoin
import com.alsoug.keswa.features.catalog.domain.usecase.SeedShopUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named

/**
 * The Android entry point, doing exactly what `Main.kt` does on desktop.
 *
 * The one addition is `androidContext`: the platform module needs a [android.content.Context] to
 * find the database file, and this is the only place in the app that knows one exists.
 */
class KeswaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val koin = initKoin { androidContext(this@KeswaApplication) }.koin

        warmUpDatabase(
            database = koin.get(),
            seed = koin.get(),
            scope = koin.get(named(APPLICATION_SCOPE)),
            platform = koin.get(),
        )
    }
}

/**
 * Forces the first connection so the schema is created and any migration runs before a screen needs
 * data, and seeds what a fresh install cannot work without.
 *
 * Deliberately off the main thread and not awaited, for the same reasons as on desktop: a migration
 * on a large ledger must never freeze the UI, and `runBlocking` is a forbidden pattern.
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
            seed().getOrThrow()
        }
            .onSuccess { platform.log(LogLevel.INFO, TAG, "database ready") }
            .onFailure { platform.log(LogLevel.ERROR, TAG, "database failed to open", it) }
    }
}

private const val TAG = "Startup"
