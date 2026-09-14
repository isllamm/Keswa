package com.alsoug.keswa.core.di

import com.alsoug.keswa.core.coroutines.DefaultDispatcherProvider
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import org.koin.dsl.module

/**
 * Shared infrastructure bindings. Platform implementations live in `:composeApp`.
 */
val coreModule = module {
    // Infrastructure
    single<DispatcherProvider> { DefaultDispatcherProvider() }
}
