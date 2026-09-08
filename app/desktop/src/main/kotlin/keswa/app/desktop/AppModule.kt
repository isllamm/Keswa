package keswa.app.desktop

import keswa.app.desktop.platform.DesktopAppPaths
import keswa.app.desktop.platform.FileDeviceIdProvider
import keswa.app.desktop.platform.JvmArgon2Hasher
import keswa.core.common.AppPaths
import keswa.core.common.Clock
import keswa.core.common.DeviceIdProvider
import keswa.core.common.MonotonicUlidFactory
import keswa.core.common.SystemClock
import keswa.data.JvmSqlDriverFactory
import keswa.data.Seeder
import keswa.data.SqlStoreContext
import keswa.data.SqlUnitOfWork
import keswa.data.UnitOfWork
import keswa.data.auth.SqlAppUserRepository
import keswa.data.catalog.SqlBrandRepository
import keswa.data.catalog.SqlCategoryRepository
import keswa.data.catalog.SqlProductRepository
import keswa.data.catalog.SqlStockLevelRepository
import keswa.data.db.KeswaDatabase
import keswa.domain.InMemoryPrincipalHolder
import keswa.domain.PrincipalHolder
import keswa.domain.auth.AppUserRepository
import keswa.domain.auth.PasswordHasher
import keswa.domain.auth.StoreContext
import keswa.domain.auth.UnlockWithPin
import keswa.domain.catalog.BrandRepository
import keswa.domain.catalog.CategoryRepository
import keswa.domain.catalog.CreateProductWithVariants
import keswa.domain.catalog.ProductRepository
import keswa.domain.catalog.StockLevelRepository
import keswa.feature.auth.PinStore
import keswa.feature.catalog.CatalogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// Must be declared before `appModule`: it's referenced inside that module{} block, and Kotlin
// initializes top-level properties in one file in textual order. Declaring it after appModule
// compiles fine but means `single(WRITER_DISPATCHER)` registers under whatever this property's
// default value is at that point (effectively null) — a real bug this project hit once already:
// the registration silently used a different qualifier than every later get(WRITER_DISPATCHER)
// lookup, which then failed with "No definition found".
private val WRITER_DISPATCHER = org.koin.core.qualifier.named("writerDispatcher")

@OptIn(ExperimentalCoroutinesApi::class)
val appModule = module {
    single<AppPaths> { DesktopAppPaths() }
    single<Clock> { SystemClock }
    single { MonotonicUlidFactory(get()) }
    single<DeviceIdProvider> { FileDeviceIdProvider(get()) }
    single<PasswordHasher> { JvmArgon2Hasher() }
    single<PrincipalHolder> { InMemoryPrincipalHolder() }

    single { Dispatchers.IO } // general repository reads
    single(WRITER_DISPATCHER) { Dispatchers.IO.limitedParallelism(1) } // UnitOfWork's single writer

    single<KeswaDatabase> { JvmSqlDriverFactory(get()).create().let { KeswaDatabase(it) } }
    single<StoreContext> { SqlStoreContext(get(), get()) }
    single<AppUserRepository> {
        val storeContext = get<StoreContext>()
        SqlAppUserRepository(get(), get(), get(), storeContext::tenantId, storeContext::storeId)
    }
    single<UnitOfWork> { SqlUnitOfWork(get(), get(), get(), get(WRITER_DISPATCHER)) }
    single { Seeder(get(), get(), get(), get()) }
    single { UnlockWithPin(get(), get(), get(), get()) }

    single<CategoryRepository> {
        SqlCategoryRepository(get(), get(), get(), get<DeviceIdProvider>().current().value, get(), get())
    }
    single<BrandRepository> {
        SqlBrandRepository(get(), get(), get(), get<DeviceIdProvider>().current().value, get(), get())
    }
    single<ProductRepository> { SqlProductRepository(get(), get(), get(), get(), get(), get()) }
    single<StockLevelRepository> { SqlStockLevelRepository(get(), get(), get()) }
    single { CreateProductWithVariants(get()) }

    viewModel { PinStore(get(), get(), get()) }
    viewModel { CatalogStore(get(), get(), get()) }
}
