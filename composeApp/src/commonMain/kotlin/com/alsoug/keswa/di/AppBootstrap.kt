package com.alsoug.keswa.di

import com.alsoug.keswa.core.di.coreModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/**
 * Composition root. Feature modules are appended here as each phase lands.
 */
fun initKoin(declaration: KoinAppDeclaration? = null): KoinApplication =
    startKoin {
        declaration?.invoke(this)
        modules(
            coreModule,
            platformModule,
        )
    }
