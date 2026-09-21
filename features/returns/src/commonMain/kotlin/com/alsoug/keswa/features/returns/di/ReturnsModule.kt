package com.alsoug.keswa.features.returns.di

import com.alsoug.keswa.features.returns.domain.usecase.CompleteReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.FindSaleForReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.GetReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.LinkExchangeUseCase
import com.alsoug.keswa.features.returns.domain.usecase.LowestSoldPriceUseCase
import com.alsoug.keswa.features.returns.domain.usecase.PrintRefundNoteUseCase
import com.alsoug.keswa.features.returns.domain.usecase.VoidReturnUseCase
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
val returnsModule = module {
    // Domain Layer
    factory { FindSaleForReturnUseCase(get(), get(), get()) { now() } }
    factory { CompleteReturnUseCase(get(), get(), get(), get(), get(), get()) { now() } }
    factory { LowestSoldPriceUseCase(get()) }
    factory { VoidReturnUseCase(get()) { now() } }
    factory { LinkExchangeUseCase(get()) }
    factory { GetReturnUseCase(get()) }
    factory { PrintRefundNoteUseCase(get(), get(), get()) }

    // Presentation Layer
    factory { ReturnsViewModel(get(), get(), get(), get(), get(), get()) }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
