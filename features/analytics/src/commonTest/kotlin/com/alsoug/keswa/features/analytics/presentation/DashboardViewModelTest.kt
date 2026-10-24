package com.alsoug.keswa.features.analytics.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.model.AnalyticsPeriod
import com.alsoug.keswa.core.domain.model.BusyHours
import com.alsoug.keswa.core.domain.model.ColourBucket
import com.alsoug.keswa.core.domain.model.DailyPoint
import com.alsoug.keswa.core.domain.model.HeadlineKpis
import com.alsoug.keswa.core.domain.model.HourBucket
import com.alsoug.keswa.core.domain.model.Mover
import com.alsoug.keswa.core.domain.model.SellThroughRowModel
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IAnalyticsRepository
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.analytics.domain.usecase.GetBusyHoursUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetColourPerformanceUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetHeadlineKpisUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetRevenueTrendUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetSellThroughUseCase
import com.alsoug.keswa.features.analytics.domain.usecase.GetTopMoversUseCase
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardUiEffect
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardUiEvent
import com.alsoug.keswa.features.analytics.presentation.screens.dashboard.DashboardViewModel
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private class FakeAnalyticsRepository : IAnalyticsRepository {
        var headlineResult: Result<HeadlineKpis> = Result.success(
            HeadlineKpis(
                revenue = Money.ofPounds(1_000),
                refunded = Money.ZERO,
                transactions = 4,
                units = 9,
                returnedUnits = 1,
                cogs = Money.ofPounds(600),
            ),
        )
        var trendResult: Result<List<DailyPoint>> = Result.success(
            listOf(
                DailyPoint("2026-10-24", Money.ofPounds(1_000), 4),
            ),
        )
        var coloursResult: Result<List<ColourBucket>> = Result.success(
            listOf(
                ColourBucket(
                    colourId = "c1",
                    name = "Navy",
                    nameAr = "كحلي",
                    hex = "#000080",
                    sold = 5,
                    onHand = 10,
                ),
            ),
        )
        var sellThroughResult: Result<List<SellThroughRowModel>> = Result.success(
            listOf(
                SellThroughRowModel(
                    categoryId = "cat-1",
                    name = "Shirts",
                    nameAr = "قمصان",
                    sold = 7,
                    received = 10,
                ),
            ),
        )
        var busyHoursResult: Result<BusyHours> = Result.success(
            BusyHours(
                listOf(
                    HourBucket(dayOfWeek = 1, hour = 14, transactions = 3, amount = Money.ofPounds(300)),
                ),
            ),
        )
        var topMoversResult: Result<List<Mover>> = Result.success(
            listOf(
                Mover(
                    variantId = "v1",
                    sku = "SKU-1",
                    productName = "T-Shirt",
                    colourName = "Navy",
                    sold = 9,
                    revenue = Money.ofPounds(1_620),
                    onHand = 3,
                    received = 12,
                    cogs = Money.ofPounds(1_080),
                ),
            ),
        )

        var headlineCalled = 0
        var trendCalled = 0
        var coloursCalled = 0
        var sellThroughCalled = 0
        var busyHoursCalled = 0
        var moversCalled = 0

        var lastHeadlineWithCost: Boolean? = null
        var lastHeadlineFrom: Long? = null
        var lastHeadlineTo: Long? = null

        override suspend fun headline(from: Long, to: Long, withCost: Boolean): Result<HeadlineKpis> {
            headlineCalled++
            lastHeadlineFrom = from
            lastHeadlineTo = to
            lastHeadlineWithCost = withCost
            return headlineResult
        }

        override suspend fun revenueByDay(from: Long, to: Long): Result<List<DailyPoint>> {
            trendCalled++
            return trendResult
        }

        override suspend fun colourPerformance(from: Long, to: Long): Result<List<ColourBucket>> {
            coloursCalled++
            return coloursResult
        }

        override suspend fun sellThrough(from: Long, to: Long): Result<List<SellThroughRowModel>> {
            sellThroughCalled++
            return sellThroughResult
        }

        override suspend fun busyHours(from: Long, to: Long): Result<BusyHours> {
            busyHoursCalled++
            return busyHoursResult
        }

        override suspend fun topMovers(from: Long, to: Long, limit: Int, withCost: Boolean): Result<List<Mover>> {
            moversCalled++
            return topMoversResult
        }
    }

    private val now = 1_757_000_000_000L
    private lateinit var repo: FakeAnalyticsRepository
    private lateinit var sessions: InMemorySessionManager

    private fun testDispatchers(dispatcher: CoroutineDispatcher) = object : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
    }

    private fun createViewModel(dispatcher: CoroutineDispatcher): DashboardViewModel {
        return DashboardViewModel(
            headline = GetHeadlineKpisUseCase(repo, sessions) { now },
            trend = GetRevenueTrendUseCase(repo, sessions) { now },
            colours = GetColourPerformanceUseCase(repo, sessions) { now },
            sellThrough = GetSellThroughUseCase(repo, sessions) { now },
            busyHours = GetBusyHoursUseCase(repo, sessions) { now },
            movers = GetTopMoversUseCase(repo, sessions) { now },
            sessions = sessions,
            dispatchers = testDispatchers(dispatcher),
        )
    }

    @BeforeTest
    fun setUp() {
        repo = FakeAnalyticsRepository()
        sessions = InMemorySessionManager()
    }

    @Test
    fun `initial state has default period WEEK and is not loading`() = runTest {
        val vm = createViewModel(StandardTestDispatcher(testScheduler))

        val state = vm.state.value
        assertEquals(AnalyticsPeriod.WEEK, state.period)
        assertFalse(state.isLoading)
        assertTrue(state.isPermitted)
        assertFalse(state.showsCost)
        assertNull(state.kpis)
        assertTrue(state.trend.isEmpty())
        assertTrue(state.colours.isEmpty())
        assertTrue(state.sellThrough.isEmpty())
        assertTrue(state.busyHours.buckets.isEmpty())
        assertTrue(state.movers.isEmpty())
    }

    @Test
    fun `load with admin session populates all panels and displays cost`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        sessions.signIn(User("u1", "admin", "Admin", "مدير", UserRole.ADMIN), atMillis = now)
        val vm = createViewModel(dispatcher)

        vm.onEvent(DashboardUiEvent.Load)
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isPermitted)
        assertTrue(state.showsCost)
        assertEquals(Money.ofPounds(1_000), state.kpis?.revenue)
        assertEquals(Money.ofPounds(600), state.kpis?.cogs)
        assertEquals(1, state.trend.size)
        assertEquals(1, state.colours.size)
        assertEquals(1, state.sellThrough.size)
        assertEquals(1, state.busyHours.buckets.size)
        assertEquals(1, state.movers.size)
        assertEquals(1, repo.headlineCalled)
        assertEquals(true, repo.lastHeadlineWithCost)
    }

    @Test
    fun `load with seller session sets isPermitted to false and skips querying repository`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        sessions.signIn(User("u2", "seller", "Seller", "بائع", UserRole.SELLER), atMillis = now)
        val vm = createViewModel(dispatcher)

        vm.onEvent(DashboardUiEvent.Load)
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isPermitted)
        assertEquals(0, repo.headlineCalled)
        assertEquals(0, repo.trendCalled)
    }

    @Test
    fun `load without active session sets isPermitted to false`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val vm = createViewModel(dispatcher)

        vm.onEvent(DashboardUiEvent.Load)
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertFalse(state.isPermitted)
        assertEquals(0, repo.headlineCalled)
    }

    @Test
    fun `period changed event updates period and queries repository with new window`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        sessions.signIn(User("u1", "admin", "Admin", "مدير", UserRole.ADMIN), atMillis = now)
        val vm = createViewModel(dispatcher)

        vm.onEvent(DashboardUiEvent.PeriodChanged(AnalyticsPeriod.TODAY))
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(AnalyticsPeriod.TODAY, state.period)
        assertEquals(1, repo.headlineCalled)
        assertEquals(now, repo.lastHeadlineTo)
        assertEquals(now - 1 * 24L * 60 * 60 * 1000, repo.lastHeadlineFrom)
    }

    @Test
    fun `refresh event reloads dashboard figures for current period`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        sessions.signIn(User("u1", "admin", "Admin", "مدير", UserRole.ADMIN), atMillis = now)
        val vm = createViewModel(dispatcher)

        vm.onEvent(DashboardUiEvent.Load)
        testScheduler.advanceUntilIdle()
        assertEquals(1, repo.headlineCalled)

        vm.onEvent(DashboardUiEvent.Refresh)
        testScheduler.advanceUntilIdle()
        assertEquals(2, repo.headlineCalled)
    }

    @Test
    fun `headline failure emits ShowError effect and clears loading`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        sessions.signIn(User("u1", "admin", "Admin", "مدير", UserRole.ADMIN), atMillis = now)
        repo.headlineResult = Result.failure(RuntimeException("Database unreachable"))
        val vm = createViewModel(dispatcher)

        val effects = mutableListOf<DashboardUiEffect>()
        val job = launch { vm.effect.collect { effects.add(it) } }
        runCurrent()

        vm.onEvent(DashboardUiEvent.Load)
        testScheduler.advanceUntilIdle()
        job.cancel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(1, effects.size)
        assertIs<DashboardUiEffect.ShowError>(effects.single())
    }

    @Test
    fun `partial panel failure gracefully falls back without cancelling other panels`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        sessions.signIn(User("u1", "admin", "Admin", "مدير", UserRole.ADMIN), atMillis = now)
        repo.coloursResult = Result.failure(RuntimeException("Colours query timed out"))
        val vm = createViewModel(dispatcher)

        vm.onEvent(DashboardUiEvent.Load)
        testScheduler.advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertTrue(state.isPermitted)
        assertEquals(Money.ofPounds(1_000), state.kpis?.revenue)
        assertTrue(state.colours.isEmpty(), "Colours should fall back to empty list on error")
        assertEquals(1, state.movers.size, "Other panels should still succeed under supervisorScope")
    }
}
