package com.alsoug.keswa.di

import com.alsoug.keswa.AndroidPlatformProvider
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.database.getDatabaseBuilder
import com.alsoug.keswa.core.database.getKeswaDatabase
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.core.platform.IPlatformProvider
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.platform.JvmPasswordHasher
import com.alsoug.keswa.core.printing.AndroidReceiptRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Platform
    single<IPlatformProvider> { AndroidPlatformProvider() }
    single<IReceiptRenderer> { AndroidReceiptRenderer() }
    // The same hasher as the till, deliberately: a user created there signs in here, and a
    // different iteration count would mean nobody could.
    single<IPasswordHasher> { JvmPasswordHasher(get<DispatcherProvider>()) }

    // Application-lifetime scope, injected rather than GlobalScope (CODE_GUIDELINES forbidden
    // patterns); SupervisorJob so one failed startup task cannot cancel the rest.
    single(named(APPLICATION_SCOPE)) {
        CoroutineScope(SupervisorJob() + get<DispatcherProvider>().io)
    }

    // Database — the one place in the app that needs a Context.
    single<KeswaDatabase> {
        getKeswaDatabase(getDatabaseBuilder(androidContext()), get<DispatcherProvider>())
    }
}
