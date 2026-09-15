package com.alsoug.keswa.features.auth.di

import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.features.auth.data.RecoveryCodeGenerator
import com.alsoug.keswa.features.auth.data.SettingsRecoveryCodeStore
import com.alsoug.keswa.features.auth.domain.usecase.BootstrapFirstAdminUseCase
import com.alsoug.keswa.features.auth.domain.usecase.ChangeOwnSecretUseCase
import com.alsoug.keswa.features.auth.domain.usecase.CreateUserUseCase
import com.alsoug.keswa.features.auth.domain.usecase.ListSellersUseCase
import com.alsoug.keswa.features.auth.domain.usecase.NeedsFirstRunSetupUseCase
import com.alsoug.keswa.features.auth.domain.usecase.RecoverWithCodeUseCase
import com.alsoug.keswa.features.auth.domain.usecase.RecoveryCodeStore
import com.alsoug.keswa.features.auth.domain.usecase.ResetUserSecretUseCase
import com.alsoug.keswa.features.auth.domain.usecase.SignInUseCase
import com.alsoug.keswa.features.auth.presentation.screens.signin.SignInViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
val authModule = module {
    // Data Layer
    single<RecoveryCodeStore> { SettingsRecoveryCodeStore(get<KeswaDatabase>().settingDao()) }
    single { RecoveryCodeGenerator(get()) }

    // Domain Layer
    factory { SignInUseCase(get(), get(), get()) { Clock.System.now().toEpochMilliseconds() } }
    factory { NeedsFirstRunSetupUseCase(get()) }
    factory {
        val generator: RecoveryCodeGenerator = get()
        BootstrapFirstAdminUseCase(get(), get(), get(), get()) { generator.newCode() }
    }
    factory { RecoverWithCodeUseCase(get(), get(), get()) }
    factory { CreateUserUseCase(get(), get(), get(), get()) }
    factory { ResetUserSecretUseCase(get(), get(), get()) }
    factory { ChangeOwnSecretUseCase(get(), get(), get()) }
    factory { ListSellersUseCase(get()) }

    // Presentation Layer
    factory { SignInViewModel(get(), get(), get(), get(), get(), get()) }
}
