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
import keswa.data.db.KeswaDatabase
import keswa.domain.InMemoryPrincipalHolder
import keswa.domain.PrincipalHolder
import keswa.domain.auth.AppUserRepository
import keswa.domain.auth.PasswordHasher
import keswa.domain.auth.StoreContext
import keswa.domain.auth.UnlockWithPin
import keswa.feature.auth.PinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

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

    viewModel { PinStore(get(), get(), get()) }
}

private val WRITER_DISPATCHER = org.koin.core.qualifier.named("writerDispatcher")
