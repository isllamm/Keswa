package com.alsoug.keswa.di

import androidx.room.Room
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.getKeswaDatabase
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardViewModel
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInViewModel
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserViewModel
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorViewModel
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustViewModel
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountViewModel
import com.alsoug.keswa.features.inventory.presentation.screens.importer.ImportViewModel
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingViewModel
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsViewModel
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftViewModel
import com.alsoug.keswa.features.sell.presentation.screens.till.TillViewModel
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class KoinGraphTest {

    @AfterTest
    fun tearDown() = stopKoin()

    /**
     * Swaps the real database for an in-memory one.
     *
     * Loaded after startup, which is when Koin lets a definition replace an earlier one — the
     * alternative would have this test create a file in the developer's home directory.
     */
    private fun startWithInMemoryDatabase() = initKoin().koin.also { koin ->
        koin.loadModules(
            listOf(
                module {
                    single<KeswaDatabase> {
                        getKeswaDatabase(
                            Room.inMemoryDatabaseBuilder<KeswaDatabase>(),
                            get<DispatcherProvider>(),
                        )
                    }
                },
            ),
        )
    }

    @Test
    fun `composition root resolves every registered dependency`() {
        val koin = initKoin().koin

        assertNotNull(koin.get<DispatcherProvider>())
        assertNotNull(koin.get<IPlatformProvider>())
    }

    /**
     * Every screen in the app, constructed for real.
     *
     * A ViewModel with fifteen constructor arguments fails at the click that opens it, not at
     * build time — so resolving each one here is what turns a missing `get()` in a feature module
     * into a red test rather than a blank screen in a shop.
     */
    @Test
    fun `every screen's ViewModel can be constructed`() {
        val koin = startWithInMemoryDatabase()

        assertNotNull(koin.get<SignInViewModel>())
        assertNotNull(koin.get<CatalogBrowserViewModel>())
        assertNotNull(koin.get<ProductEditorViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
        assertNotNull(koin.get<TillViewModel>())
        assertNotNull(koin.get<ShiftViewModel>())
        assertNotNull(koin.get<ReceivingViewModel>())
        assertNotNull(koin.get<CountViewModel>())
        assertNotNull(koin.get<AdjustViewModel>())
        assertNotNull(koin.get<ImportViewModel>())
        assertNotNull(koin.get<ReturnsViewModel>())
        assertNotNull(koin.get<DashboardViewModel>())
    }
}
