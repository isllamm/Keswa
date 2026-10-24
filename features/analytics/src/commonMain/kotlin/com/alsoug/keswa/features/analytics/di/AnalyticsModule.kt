package com.alsoug.keswa.features.analytics.di

import com.alsoug.keswa.features.analytics.domain.usecase.GetBusyHoursUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetColourPerformanceUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetHeadlineKpisUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetRevenueTrendUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetSellThroughUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetTopMoversUseCase
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
val analyticsModule = module {
    // Domain Layer
    factory { GetHeadlineKpisUseCase(get(), get()) { now() } }
    factory { GetRevenueTrendUseCase(get(), get()) { now() } }
    factory { GetColourPerformanceUseCase(get(), get()) { now() } }
    factory { GetSellThroughUseCase(get(), get()) { now() } }
    factory { GetBusyHoursUseCase(get(), get()) { now() } }
    factory { GetTopMoversUseCase(get(), get()) { now() } }

    // Presentation Layer
    factory { DashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
