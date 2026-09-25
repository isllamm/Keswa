package com.alsoug.keswa.features.inventory.di

import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.AdjustStockUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ApplyCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.CountVariantUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.CurrentCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.DiscardCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.DiscardReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichCountLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichReceiptLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.GetReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.GetVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.InventoryLabelUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.ObserveDraftReceiptsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ParseCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintHangTagsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintSingleVariantLabelUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RecentCountsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RecentReceiptsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ReceivingUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.RemoveReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StockCountUseCases
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

    factory { EnsureVariantBarcodeUseCase(get()) }
    factory { PrintSingleVariantLabelUseCase(get(), get(), get(), get(), get(), get()) { now() } }
    factory { PrintHangTagsUseCase(get(), get(), get(), get(), get(), get()) { now() } }
    factory {
        InventoryLabelUseCases(
            ensureBarcode = get(),
            printSingleLabel = get(),
            printHangTags = get(),
        )
    }

    factory { StartReceiptUseCase(get(), get(), get()) { now() } }
    factory { AddReceiptLineUseCase(get(), get(), get(), get()) }
    factory { RemoveReceiptLineUseCase(get(), get()) }
    factory { PostReceiptUseCase(get(), get()) { now() } }
    factory { DiscardReceiptUseCase(get(), get()) }
    factory { GetReceiptUseCase(get()) }
    factory { RecentReceiptsUseCase(get()) }
    factory { ObserveDraftReceiptsUseCase(get()) }
    factory {
        ReceivingUseCases(
            start = get(),
            addLine = get(),
            removeLine = get(),
            post = get(),
            discard = get(),
            getById = get(),
            recent = get(),
            observeDrafts = get(),
        )
    }
    factory { EnrichReceiptLinesUseCase(get(), get()) }

    factory { StartCountUseCase(get(), get(), get()) { now() } }
    factory { CountVariantUseCase(get(), get(), get()) }
    factory { PostCountUseCase(get(), get()) { now() } }
    factory { DiscardCountUseCase(get(), get()) }
    factory { CurrentCountUseCase(get()) }
    factory { RecentCountsUseCase(get()) }
    factory {
        StockCountUseCases(
            start = get(),
            countVariant = get(),
            post = get(),
            discard = get(),
            current = get(),
            recent = get(),
        )
    }
    factory { EnrichCountLinesUseCase(get()) }

    factory { AdjustStockUseCase(get(), get(), get()) { now() } }
    factory { StockHistoryUseCase(get()) }
    factory { GetVariantBarcodeUseCase(get()) }

    factory { ParseCatalogueImportUseCase() }
    factory {
        ApplyCatalogueImportUseCase(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        ) { now() }
    }

    // Presentation Layer
    factory {
        ReceivingViewModel(
            resolveLocation = get(),
            find = get(),
            receiving = get(),
            enrichLines = get(),
            labels = get(),
            dispatchers = get(),
        )
    }
    factory {
        CountViewModel(
            resolveLocation = get(),
            find = get(),
            counts = get(),
            enrichLines = get(),
            dispatchers = get(),
        )
    }
    factory {
        AdjustViewModel(
            resolveLocation = get(),
            find = get(),
            adjust = get(),
            history = get(),
            getBarcode = get(),
            labels = get(),
            dispatchers = get(),
        )
    }
    factory {
        ImportViewModel(
            resolveLocation = get(),
            parse = get(),
            apply = get(),
            dispatchers = get(),
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
