package com.alsoug.keswa.features.sell.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.model.Location
import com.alsoug.keswa.core.domain.model.LocationType
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.PriceList
import com.alsoug.keswa.core.domain.model.PriceListType
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.model.ZReport
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.IShiftRepository
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.sell.domain.usecase.CloseShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CurrentShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResolveTillContextUseCase
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftNavigation
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftUiEffect
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftUiEvent
import com.alsoug.keswa.features.sell.presentation.screens.shift.ShiftViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private class FakeLocationRepository(
    var defaultLocation: Location = Location(
        id = "loc-1",
        name = "Downtown",
        nameAr = "وسط البلد",
        type = LocationType.SHOP,
        isDefault = true,
        isActive = true,
    ),
) : ILocationRepository {
    override suspend fun default(): Result<Location?> = Result.success(defaultLocation)
    override suspend fun ensureDefault(id: String, name: String, nameAr: String): Result<Location> =
        Result.success(defaultLocation)
    override suspend fun getAll(): Result<List<Location>> = Result.success(listOf(defaultLocation))
}

private class FakePriceRepository(
    var defaultPriceList: PriceList = PriceList(
        id = "pl-1",
        name = "Retail",
        nameAr = "قطاعي",
        type = PriceListType.RETAIL,
        isDefault = true,
        isActive = true,
    ),
) : IPriceRepository {
    override suspend fun defaultList(): Result<PriceList?> = Result.success(defaultPriceList)
    override suspend fun ensureDefaultList(id: String, name: String, nameAr: String): Result<PriceList> =
        Result.success(defaultPriceList)
    override suspend fun listsOfType(type: PriceListType): Result<List<PriceList>> =
        Result.success(listOf(defaultPriceList))
    override suspend fun createList(id: String, name: String, nameAr: String, type: PriceListType): Result<PriceList> =
        Result.success(defaultPriceList)
    override suspend fun effectivePrice(variantId: String, priceListId: String, at: Long): Result<Money?> =
        Result.success(null)
    override suspend fun setPrice(id: String, variantId: String, priceListId: String, price: Money, from: Long): Result<Unit> =
        Result.success(Unit)
}

private class FakeShiftRepository : IShiftRepository {
    var activeShift: Shift? = null
    var reportToReturn: ZReport? = null

    override suspend fun open(
        id: String,
        locationId: String,
        userId: String,
        openingFloat: Money,
        atMillis: Long,
    ): Result<Shift> {
        val shift = Shift(
            id = id,
            locationId = locationId,
            openedByUserId = userId,
            openedAt = atMillis,
            openingFloat = openingFloat,
        )
        activeShift = shift
        return Result.success(shift)
    }

    override suspend fun close(
        shiftId: String,
        userId: String,
        countedCash: Money,
        note: String?,
        atMillis: Long,
    ): Result<ZReport> {
        val current = activeShift ?: return Result.failure(IllegalStateException("no active shift"))
        val closed = current.copy(
            closedAt = atMillis,
            closedByUserId = userId,
            countedCash = countedCash,
            expectedCash = current.openingFloat,
            note = note,
        )
        activeShift = null
        val report = reportToReturn ?: ZReport(
            shift = closed,
            saleCount = 0,
            voidedCount = 0,
            grossSales = Money.ZERO,
            discounts = Money.ZERO,
            tax = Money.ZERO,
            netSales = Money.ZERO,
            cashTaken = Money.ZERO,
            cardTaken = Money.ZERO,
            changeGiven = Money.ZERO,
            returnCount = 0,
            cashRefunded = Money.ZERO,
            cardRefunded = Money.ZERO,
            expectedCash = closed.openingFloat,
            countedCash = countedCash,
            salesOutsideShift = 0,
        )
        return Result.success(report)
    }

    override suspend fun current(locationId: String): Result<Shift?> = Result.success(activeShift)

    override suspend fun report(shiftId: String): Result<ZReport?> = Result.success(reportToReturn)

    override fun observeCurrent(locationId: String): Flow<Shift?> = flowOf(activeShift)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShiftViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private val locations = FakeLocationRepository()
    private val prices = FakePriceRepository()
    private val shifts = FakeShiftRepository()
    private val sessions = InMemorySessionManager()
    private var now = 1_000_000L

    private val seller = User("u1", "mona", "Mona", "منى", UserRole.SELLER)

    private fun createViewModel() = ShiftViewModel(
        resolveContext = ResolveTillContextUseCase(locations, prices),
        currentShift = CurrentShiftUseCase(shifts),
        closeShift = CloseShiftUseCase(shifts, sessions) { now },
        dispatchers = dispatchers,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(scheduler)
        sessions.signIn(seller, now)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load fetches active shift into state`() = runTest(scheduler) {
        val shift = Shift(
            id = "shift-1",
            locationId = "loc-1",
            openedByUserId = "u1",
            openedAt = 500_000L,
            openingFloat = Money.ofPounds(200),
        )
        shifts.activeShift = shift

        val vm = createViewModel()
        vm.onEvent(ShiftUiEvent.Load)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("shift-1", state.shift?.id)
        assertEquals(Money.ofPounds(200), state.shift?.openingFloat)
    }

    @Test
    fun `counted cash and note changes update state`() = runTest(scheduler) {
        val vm = createViewModel()

        vm.onEvent(ShiftUiEvent.CountedCashChanged("250.50"))
        vm.onEvent(ShiftUiEvent.NoteChanged("Midday shift handoff"))

        assertEquals("250.50", vm.state.value.countedCash)
        assertEquals("Midday shift handoff", vm.state.value.note)
    }

    @Test
    fun `close with invalid money format emits error effect and keeps shift`() = runTest(scheduler) {
        shifts.activeShift = Shift(
            id = "shift-1",
            locationId = "loc-1",
            openedByUserId = "u1",
            openedAt = 500_000L,
            openingFloat = Money.ofPounds(200),
        )

        val vm = createViewModel()
        val effects = mutableListOf<ShiftUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(ShiftUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ShiftUiEvent.CountedCashChanged("invalid-amount"))
        vm.onEvent(ShiftUiEvent.Close)
        advanceUntilIdle()

        assertEquals(1, effects.size)
        assertIs<ShiftUiEffect.ShowError>(effects.first())
        assertNotNull(shifts.activeShift)
        job.cancel()
    }

    @Test
    fun `close with drawer difference emits discrepancy warning and updates state with ZReport`() = runTest(scheduler) {
        shifts.activeShift = Shift(
            id = "shift-1",
            locationId = "loc-1",
            openedByUserId = "u1",
            openedAt = 500_000L,
            openingFloat = Money.ofPounds(200),
        )

        val vm = createViewModel()
        val effects = mutableListOf<ShiftUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(ShiftUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ShiftUiEvent.CountedCashChanged("250.00")) // +50 difference
        vm.onEvent(ShiftUiEvent.Close)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.report)
        assertEquals(Money.ofPounds(250), vm.state.value.report?.countedCash)
        assertEquals(Money.ofPounds(200), vm.state.value.report?.expectedCash)

        assertEquals(1, effects.size)
        assertIs<ShiftUiEffect.ShowMessage>(effects.first())
        assertNull(shifts.activeShift)
        job.cancel()
    }

    @Test
    fun `close with exact counted cash emits no difference warning`() = runTest(scheduler) {
        shifts.activeShift = Shift(
            id = "shift-1",
            locationId = "loc-1",
            openedByUserId = "u1",
            openedAt = 500_000L,
            openingFloat = Money.ofPounds(200),
        )

        val vm = createViewModel()
        val effects = mutableListOf<ShiftUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(ShiftUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ShiftUiEvent.CountedCashChanged("200.00")) // exact
        vm.onEvent(ShiftUiEvent.Close)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.report)
        assertTrue(effects.isEmpty())
        assertNull(shifts.activeShift)
        job.cancel()
    }

    @Test
    fun `done event emits Back navigation`() = runTest(scheduler) {
        val vm = createViewModel()
        val navigations = mutableListOf<ShiftNavigation>()
        val job = launch { vm.navigation.collect { navigations += it } }
        runCurrent()

        vm.onEvent(ShiftUiEvent.Done)
        advanceUntilIdle()

        assertEquals(1, navigations.size)
        assertEquals(ShiftNavigation.Back, navigations.first())
        job.cancel()
    }
}
