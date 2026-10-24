package com.alsoug.keswa.features.settings.di

import com.alsoug.keswa.features.settings.domain.usecase.EnrolDeviceUseCase
import com.alsoug.keswa.features.settings.domain.usecase.GetSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.GetSyncStatusUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestLabelUseCase
import com.alsoug.keswa.features.settings.domain.usecase.PrintTestPageUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SaveSettingsUseCase
import com.alsoug.keswa.features.settings.domain.usecase.SyncNowUseCase
import com.alsoug.keswa.features.settings.presentation.screens.settings.SettingsViewModel
import org.koin.dsl.module

val settingsModule = module {
    // Domain Layer
    factory { GetSettingsUseCase(get()) }
    factory { SaveSettingsUseCase(get()) }
    factory { PrintTestPageUseCase(get(), get(), get()) }
    factory { PrintTestLabelUseCase(get(), get(), get()) }
    factory { GetSyncStatusUseCase(get()) }
    factory { EnrolDeviceUseCase(get()) }
    factory { SyncNowUseCase(get()) }

    // Presentation Layer
    factory { SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}
