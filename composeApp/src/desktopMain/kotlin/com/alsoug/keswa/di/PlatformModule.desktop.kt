package com.alsoug.keswa.di

import com.alsoug.keswa.DesktopPlatformProvider
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.appDataDirectory
import com.alsoug.keswa.core.database.getDatabaseBuilder
import com.alsoug.keswa.core.database.getKeswaDatabase
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.platform.DesktopSyncTokenStore
import com.alsoug.keswa.core.platform.ISyncTokenStore
import com.alsoug.keswa.core.sync.SyncScheduler
import com.alsoug.keswa.core.sync.createSyncHttpClient
import com.alsoug.keswa.core.platform.JvmPasswordHasher
import com.alsoug.keswa.core.printing.DesktopReceiptRenderer
import com.alsoug.keswa.core.platform.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Platform
    single<IPlatformProvider> { DesktopPlatformProvider() }
    single<IReceiptRenderer> { DesktopReceiptRenderer() }
    single<IPasswordHasher> { JvmPasswordHasher(get<DispatcherProvider>()) }

    // Application-lifetime scope. Injected rather than GlobalScope, per the forbidden-patterns
    // table in CODE_GUIDELINES; SupervisorJob so one failed startup task cannot cancel the rest.
    single(named(APPLICATION_SCOPE)) {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().io)
    }

    // Sync — bound per platform because KD-007 answers "where does a secret live" differently on a
    // shop PC and on a handheld, and because the scheduler needs the application-lifetime scope.
    single<ISyncTokenStore> { DesktopSyncTokenStore(get()) }
    single { createSyncHttpClient() }
    single {
        SyncScheduler(
            engine = get(),
            scope = get(named(APPLICATION_SCOPE)),
            platform = get(),
        )
    }

    // Database
    single<KeswaDatabase> {
        val platform: IPlatformProvider = get()
        // Logged at startup so support can find the file — until this shop enrols a second
        // device it is still the only copy of its history.
        platform.log(LogLevel.INFO, TAG, "database directory: ${appDataDirectory().absolutePath}")
        getKeswaDatabase(getDatabaseBuilder(), get<DispatcherProvider>())
    }
}


private const val TAG = "Startup"
