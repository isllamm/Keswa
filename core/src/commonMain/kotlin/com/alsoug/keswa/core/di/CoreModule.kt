package com.alsoug.keswa.core.di

import com.alsoug.keswa.core.coroutines.DefaultDispatcherProvider
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.data.repository.CategoryRepositoryImpl
import com.alsoug.keswa.core.data.repository.ColourRepositoryImpl
import com.alsoug.keswa.core.data.repository.ProductRepositoryImpl
import com.alsoug.keswa.core.data.repository.SettingsRepositoryImpl
import com.alsoug.keswa.core.data.repository.VariantRepositoryImpl
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.dsl.module

/**
 * Shared infrastructure. [KeswaDatabase] itself is bound per platform in `:composeApp`, because
 * only the platform knows where its file belongs.
 */
@OptIn(ExperimentalTime::class)
val coreModule = module {
    // Infrastructure
    single<DispatcherProvider> { DefaultDispatcherProvider() }
    single<IdGenerator> { UuidIdGenerator() }

    // Data Layer — repositories
    single<ICategoryRepository> {
        CategoryRepositoryImpl(get<KeswaDatabase>().categoryDao()) { now() }
    }
    single<IProductRepository> {
        ProductRepositoryImpl(
            get<KeswaDatabase>().productDao(),
            get<KeswaDatabase>().categoryDao(),
        ) { now() }
    }
    single<IColourRepository> { ColourRepositoryImpl(get<KeswaDatabase>().colourDao()) }
    single<ISettingsRepository> { SettingsRepositoryImpl(get<KeswaDatabase>().settingDao()) }
    single<IVariantRepository> {
        VariantRepositoryImpl(
            get<KeswaDatabase>().variantDao(),
            get<KeswaDatabase>().variantBarcodeDao(),
            get<KeswaDatabase>().stockLedgerDao(),
        ) { now() }
    }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
