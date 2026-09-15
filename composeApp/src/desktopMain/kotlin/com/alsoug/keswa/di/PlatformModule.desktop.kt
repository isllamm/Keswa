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
import com.alsoug.keswa.core.platform.DesktopPasswordHasher
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
    single<IPasswordHasher> { DesktopPasswordHasher(get<DispatcherProvider>()) }

    // Application-lifetime scope. Injected rather than GlobalScope, per the forbidden-patterns
    // table in CODE_GUIDELINES; SupervisorJob so one failed startup task cannot cancel the rest.
    single(named(APPLICATION_SCOPE)) {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().io)
    }

    // Database
    single<KeswaDatabase> {
        val platform: IPlatformProvider = get()
        // Logged at startup so support can find the file — it is the shop's only copy of its
        // history until sync ships in Phase 9.
        platform.log(LogLevel.INFO, TAG, "database directory: ${appDataDirectory().absolutePath}")
        getKeswaDatabase(getDatabaseBuilder(), get<DispatcherProvider>())
    }
}

const val APPLICATION_SCOPE = "applicationScope"

private const val TAG = "Startup"
