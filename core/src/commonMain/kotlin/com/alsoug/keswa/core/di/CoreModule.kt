package com.alsoug.keswa.core.di

import com.alsoug.keswa.core.coroutines.DefaultDispatcherProvider
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.data.repository.AnalyticsRepositoryImpl
import com.alsoug.keswa.core.data.repository.CategoryRepositoryImpl
import com.alsoug.keswa.core.data.repository.ColourRepositoryImpl
import com.alsoug.keswa.core.data.repository.ProductRepositoryImpl
import com.alsoug.keswa.core.data.repository.HeldSaleRepositoryImpl
import com.alsoug.keswa.core.data.repository.LocationRepositoryImpl
import com.alsoug.keswa.core.data.repository.PriceRepositoryImpl
import com.alsoug.keswa.core.data.repository.SaleRepositoryImpl
import com.alsoug.keswa.core.data.repository.SaleReturnRepositoryImpl
import com.alsoug.keswa.core.data.repository.SellableRepositoryImpl
import com.alsoug.keswa.core.data.repository.SettingsRepositoryImpl
import com.alsoug.keswa.core.data.repository.StockAdjustmentRepositoryImpl
import com.alsoug.keswa.core.data.repository.StockCountRepositoryImpl
import com.alsoug.keswa.core.data.repository.StockReceiptRepositoryImpl
import com.alsoug.keswa.core.data.repository.ShiftRepositoryImpl
import com.alsoug.keswa.core.data.repository.UserRepositoryImpl
import com.alsoug.keswa.core.data.repository.VariantRepositoryImpl
import com.alsoug.keswa.core.database.KeswaDatabase
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.UuidIdGenerator
import com.alsoug.keswa.core.domain.repository.IAnalyticsRepository
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IHeldSaleRepository
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.ISaleReturnRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository
import com.alsoug.keswa.core.domain.repository.IStockAdjustmentRepository
import com.alsoug.keswa.core.domain.repository.IStockCountRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.domain.repository.IShiftRepository
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.session.ICredentialVerifier
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.LockoutAwareCredentialVerifier
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.core.printing.transport.TcpTransport
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

    // Network printers, so one shared implementation covers every platform and every feature
    single<TransportFactory> { TransportFactory { host, port -> TcpTransport(host, port, get()) } }

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
    single<ILocationRepository> { LocationRepositoryImpl(get<KeswaDatabase>().locationDao()) }
    single<IColourRepository> { ColourRepositoryImpl(get<KeswaDatabase>().colourDao()) }
    single<ISettingsRepository> { SettingsRepositoryImpl(get<KeswaDatabase>().settingDao()) }
    single<IUserRepository> { UserRepositoryImpl(get<KeswaDatabase>().userDao()) { now() } }

    // Session — in memory only, so closing the app signs everyone out (correct for a shared till)
    single<ISessionManager> { InMemorySessionManager() }

    // Approving one action without disturbing the session — the till's discounts and voids, and
    // a return outside the policy window. Feeds the same lockout counter as sign-in.
    single<ICredentialVerifier> { LockoutAwareCredentialVerifier(get(), get()) { now() } }
    single<IVariantRepository> {
        VariantRepositoryImpl(
            get<KeswaDatabase>().variantDao(),
            get<KeswaDatabase>().variantBarcodeDao(),
            get<KeswaDatabase>().stockLedgerDao(),
        ) { now() }
    }

    // Selling — the sale repository takes the database itself because a sale spans DAOs and has to
    // commit as one transaction
    single<ISaleRepository> {
        SaleRepositoryImpl(
            get(),
            get<KeswaDatabase>().saleDao(),
            get<KeswaDatabase>().stockLedgerDao(),
            get(),
        )
    }
    single<IAnalyticsRepository> { AnalyticsRepositoryImpl(get<KeswaDatabase>().analyticsDao()) }
    single<ISaleReturnRepository> {
        SaleReturnRepositoryImpl(
            get(),
            get<KeswaDatabase>().saleReturnDao(),
            get<KeswaDatabase>().saleDao(),
            get<KeswaDatabase>().stockLedgerDao(),
            get(),
        )
    }
    single<IShiftRepository> {
        ShiftRepositoryImpl(
            get<KeswaDatabase>().shiftDao(),
            get<KeswaDatabase>().saleDao(),
            get<KeswaDatabase>().saleReturnDao(),
        )
    }
    single<IHeldSaleRepository> {
        HeldSaleRepositoryImpl(get(), get<KeswaDatabase>().heldSaleDao())
    }
    single<IPriceRepository> { PriceRepositoryImpl(get<KeswaDatabase>().priceDao()) }
    single<ISellableRepository> { SellableRepositoryImpl(get<KeswaDatabase>().sellableDao()) }

    // Receiving and counting — posting spans DAOs, so these take the database too
    single<IStockReceiptRepository> {
        StockReceiptRepositoryImpl(
            get(),
            get<KeswaDatabase>().stockReceiptDao(),
            get<KeswaDatabase>().variantDao(),
            get<KeswaDatabase>().stockLedgerDao(),
            get(),
        ) { now() }
    }
    single<IStockCountRepository> {
        StockCountRepositoryImpl(
            get(),
            get<KeswaDatabase>().stockCountDao(),
            get<KeswaDatabase>().variantDao(),
            get<KeswaDatabase>().stockLedgerDao(),
            get(),
        )
    }
    single<IStockAdjustmentRepository> {
        StockAdjustmentRepositoryImpl(
            get<KeswaDatabase>().stockLedgerDao(),
            get<KeswaDatabase>().variantDao(),
        )
    }
}

@OptIn(ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
