package com.alsoug.keswa.di

import com.alsoug.keswa.DesktopPlatformProvider
import com.alsoug.keswa.core.platform.IPlatformProvider
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    // Platform
    single<IPlatformProvider> { DesktopPlatformProvider() }
}
