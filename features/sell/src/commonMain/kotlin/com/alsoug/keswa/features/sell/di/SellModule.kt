package com.alsoug.keswa.features.sell.di

import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CloseShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CompleteSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CurrentShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.DiscardHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.FindSellableUseCase
import com.alsoug.keswa.features.sell.domain.usecase.HoldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ListHeldSalesUseCase
import com.alsoug.keswa.features.sell.domain.usecase.OpenShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.PrintReceiptUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ReauthenticateUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResolveTillContextUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResumeHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ShiftReportUseCase
import com.alsoug.keswa.features.sell.domain.usecase.VoidSaleUseCase
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftViewModel
import com.alsoug.keswa.features.sell.presentation.screens.till.TillViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
val sellModule = module {
    // Domain Layer
    factory { CalculateBasketTotalUseCase() }
    factory { ResolveTillContextUseCase(get(), get()) }
    factory { FindSellableUseCase(get()) { now() } }
    factory { CompleteSaleUseCase(get(), get(), get(), get(), get()) { now() } }
    factory { PrintReceiptUseCase(get(), get(), get(), get()) }
    factory { ReauthenticateUseCase(get()) }
    factory { VoidSaleUseCase(get()) { now() } }
    factory { HoldSaleUseCase(get(), get(), get()) { now() } }
    factory { ResumeHeldSaleUseCase(get(), get()) { now() } }
    factory { ListHeldSalesUseCase(get()) }
    factory { DiscardHeldSaleUseCase(get()) }
    factory { OpenShiftUseCase(get(), get(), get()) { now() } }
    factory { CloseShiftUseCase(get(), get()) { now() } }
    factory { CurrentShiftUseCase(get()) }
    factory { ShiftReportUseCase(get(), get()) }

    // Presentation Layer
    factory {
        TillViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(),
        )
    }
    factory { ShiftViewModel(get(), get(), get(), get()) }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
