package com.alsoug.keswa.features.inventory.di

import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.AdjustStockUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ApplyCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.CountVariantUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.CurrentCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.DiscardCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.DiscardReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.GetReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ParseCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintHangTagsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RecentCountsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RecentReceiptsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RemoveReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StockHistoryUseCase
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustViewModel
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountViewModel
import com.alsoug.keswa.features.inventory.presentation.screens.importer.ImportViewModel
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
val inventoryModule = module {
    // Domain Layer
    factory { ResolveStockLocationUseCase(get()) }
    factory { FindStockItemUseCase(get(), get()) { now() } }

    factory { StartReceiptUseCase(get(), get(), get()) { now() } }
    factory { AddReceiptLineUseCase(get(), get(), get()) }
    factory { RemoveReceiptLineUseCase(get(), get()) }
    factory { PostReceiptUseCase(get(), get()) { now() } }
    factory { DiscardReceiptUseCase(get(), get()) }
    factory { GetReceiptUseCase(get()) }
    factory { RecentReceiptsUseCase(get()) }
    factory { PrintHangTagsUseCase(get(), get(), get(), get(), get(), get()) { now() } }

    factory { StartCountUseCase(get(), get(), get()) { now() } }
    factory { CountVariantUseCase(get(), get(), get()) }
    factory { PostCountUseCase(get(), get()) { now() } }
    factory { DiscardCountUseCase(get(), get()) }
    factory { CurrentCountUseCase(get()) }
    factory { RecentCountsUseCase(get()) }

    factory { AdjustStockUseCase(get(), get(), get()) { now() } }
    factory { StockHistoryUseCase(get()) }

    factory { ParseCatalogueImportUseCase() }
    factory {
        ApplyCatalogueImportUseCase(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        ) { now() }
    }

    // Presentation Layer
    factory {
        ReceivingViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    factory { CountViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { AdjustViewModel(get(), get(), get(), get(), get()) }
    factory { ImportViewModel(get(), get(), get(), get()) }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
