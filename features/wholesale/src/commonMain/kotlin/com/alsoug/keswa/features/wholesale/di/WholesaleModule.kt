package com.alsoug.keswa.features.wholesale.di

import com.alsoug.keswa.features.wholesale.domain.usecase.AddPackLineUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.CheckCreditUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.CreateCustomerUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.CreatePackUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.EnsureTradePriceListUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.ExpandAssortmentPackUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.GetCustomerAccountUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.GetStatementUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.IssueCreditNoteUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.ListCustomersUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.ListDebtorsUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.ListPacksUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.OpenReceivableUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.SearchCustomersUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.TakePaymentOnAccountUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.UpdateCustomerUseCase
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomersViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.dsl.module

@OptIn(ExperimentalTime::class)
val wholesaleModule = module {
    // Domain Layer
    factory { CreateCustomerUseCase(get(), get(), get(), get()) }
    factory { UpdateCustomerUseCase(get(), get()) }
    factory { ListCustomersUseCase(get()) }
    factory { SearchCustomersUseCase(get()) }
    factory { ListDebtorsUseCase(get()) }
    factory { GetStatementUseCase(get(), get()) }
    factory { EnsureTradePriceListUseCase(get()) }

    factory { CheckCreditUseCase(get(), get()) }
    factory { OpenReceivableUseCase(get(), get(), get()) }
    factory { TakePaymentOnAccountUseCase(get(), get(), get()) { now() } }
    factory { IssueCreditNoteUseCase(get(), get()) { now() } }
    factory { GetCustomerAccountUseCase(get(), get()) { now() } }

    factory { CreatePackUseCase(get(), get(), get()) }
    factory { AddPackLineUseCase(get(), get(), get()) }
    factory { ListPacksUseCase(get()) }
    factory { ExpandAssortmentPackUseCase(get(), get(), get()) { now() } }

    // Presentation Layer
    factory { CustomersViewModel(get(), get(), get(), get(), get(), get(), { now() }, get()) }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
