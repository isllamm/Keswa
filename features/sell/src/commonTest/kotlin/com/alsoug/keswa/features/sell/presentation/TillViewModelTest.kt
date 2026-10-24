package com.alsoug.keswa.features.sell.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.model.Location
import com.alsoug.keswa.core.domain.model.LocationType
import com.alsoug.keswa.core.domain.model.PriceList
import com.alsoug.keswa.core.domain.model.PriceListType
import com.alsoug.keswa.core.domain.model.Shift
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.model.ZReport
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.IPriceRepository
import com.alsoug.keswa.core.domain.repository.IShiftRepository
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.core.session.LockoutAwareCredentialVerifier
import com.alsoug.keswa.features.sell.domain.FakeCustomers
import com.alsoug.keswa.features.sell.domain.FakeHasher
import com.alsoug.keswa.features.sell.domain.FakeHeldSaleRepository
import com.alsoug.keswa.features.sell.domain.FakeReceivables
import com.alsoug.keswa.features.sell.domain.FakeSellableRepository
import com.alsoug.keswa.features.sell.domain.FakeSettingsRepository
import com.alsoug.keswa.features.sell.domain.FakeUserRepository
import com.alsoug.keswa.features.sell.domain.RecordingRenderer
import com.alsoug.keswa.features.sell.domain.RecordingSaleRepository
import com.alsoug.keswa.features.sell.domain.RecordingTransport
import com.alsoug.keswa.features.sell.domain.SequentialIds
import com.alsoug.keswa.features.sell.domain.sellable
import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CompleteSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CurrentShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.DiscardHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.FindSellableUseCase
import com.alsoug.keswa.features.sell.domain.usecase.HoldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ListHeldSalesUseCase
import com.alsoug.keswa.features.sell.domain.usecase.OpenShiftUseCase
import com.alsoug.keswa.features.sell.domain.usecase.PrintReceiptUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ReauthenticateUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResolveTillContextUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResumeHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.VoidSaleUseCase
import com.alsoug.keswa.features.sell.presentation.screens.till.TillUiEffect
import com.alsoug.keswa.features.sell.presentation.screens.till.TillUiEvent
import com.alsoug.keswa.features.sell.presentation.screens.till.TillViewModel
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

private class TillFakeLocationRepository(
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

private class TillFakePriceRepository(
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

private class TillFakeShiftRepository : IShiftRepository {
    var activeShift: Shift? = null

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
    ): Result<ZReport> = error("unused")

    override suspend fun current(locationId: String): Result<Shift?> = Result.success(activeShift)

    override suspend fun report(shiftId: String): Result<ZReport?> = Result.success(null)

    override fun observeCurrent(locationId: String): Flow<Shift?> = flowOf(activeShift)
}

@OptIn(ExperimentalCoroutinesApi::class)
class TillViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private val now = { 1_757_000_000_000L }
    private val locations = TillFakeLocationRepository()
    private val prices = TillFakePriceRepository()
    private val shifts = TillFakeShiftRepository()
    private val sales = RecordingSaleRepository()
    private val users = FakeUserRepository()
    private val sessions = InMemorySessionManager()
    private val held = FakeHeldSaleRepository()
    private val settings = FakeSettingsRepository()
    private val renderer = RecordingRenderer()
    private val transport = RecordingTransport()

    private val testItem = sellable(
        variantId = "var-1",
        sku = "KSW-TSH-001",
        price = Money.ofPounds(200),
        onHand = 15,
    )
    private val sellables = FakeSellableRepository(listOf(testItem))

    private val seller = User("u1", "mona", "Mona", "منى", UserRole.SELLER)

    private fun createViewModel() = TillViewModel(
        resolveContext = ResolveTillContextUseCase(locations, prices),
        find = FindSellableUseCase(sellables, now),
        calculate = CalculateBasketTotalUseCase(),
        completeSale = CompleteSaleUseCase(
            sales = sales,
            sessions = sessions,
            users = users,
            customers = FakeCustomers(null),
            receivables = FakeReceivables(Money.ZERO),
            calculate = CalculateBasketTotalUseCase(),
            ids = SequentialIds("sale"),
            now = now,
        ),
        printReceipt = PrintReceiptUseCase(settings, sales, renderer) { _, _ -> transport },
        reauthenticate = ReauthenticateUseCase(LockoutAwareCredentialVerifier(users, FakeHasher(), now)),
        voidSale = VoidSaleUseCase(sales, now),
        holdSale = HoldSaleUseCase(held, sessions, SequentialIds("hold"), now),
        resumeHeld = ResumeHeldSaleUseCase(held, sellables, now),
        listHeld = ListHeldSalesUseCase(held),
        discardHeld = DiscardHeldSaleUseCase(held),
        currentShift = CurrentShiftUseCase(shifts),
        openShift = OpenShiftUseCase(shifts, sessions, SequentialIds("shift"), now),
        settings = settings,
        dispatchers = dispatchers,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(scheduler)
        sessions.signIn(seller, now())
        shifts.activeShift = Shift(
            id = "shift-1",
            locationId = "loc-1",
            openedByUserId = "u1",
            openedAt = now(),
            openingFloat = Money.ofPounds(300),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load initializes till context and detects active shift`() = runTest(scheduler) {
        val vm = createViewModel()
        vm.onEvent(TillUiEvent.Load)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.shift)
        assertEquals("shift-1", state.shift.id)
        assertTrue(state.basket.isEmpty)
    }

    @Test
    fun `scan known barcode adds item to basket and updates totals`() = runTest(scheduler) {
        val vm = createViewModel()
        vm.onEvent(TillUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(TillUiEvent.Scanned("KSW-TSH-001"))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(1, state.basket.lines.size)
        assertEquals("var-1", state.basket.lines.first().variantId)
        assertEquals(1, state.basket.lines.first().quantity)
        assertEquals(Money.ofPounds(200), state.totals.total)
    }

    @Test
    fun `scan unknown barcode emits error effect and leaves basket empty`() = runTest(scheduler) {
        val vm = createViewModel()
        val effects = mutableListOf<TillUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(TillUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(TillUiEvent.Scanned("UNKNOWN-999"))
        advanceUntilIdle()

        assertEquals(1, effects.size)
        assertIs<TillUiEffect.ShowError>(effects.first())
        assertTrue(vm.state.value.basket.isEmpty)
        job.cancel()
    }

    @Test
    fun `quantity change and remove line adjust basket and totals`() = runTest(scheduler) {
        val vm = createViewModel()
        vm.onEvent(TillUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(TillUiEvent.Scanned("KSW-TSH-001"))
        advanceUntilIdle()
        assertEquals(Money.ofPounds(200), vm.state.value.totals.total)

        vm.onEvent(TillUiEvent.QuantityChanged(0, 3))
        assertEquals(3, vm.state.value.basket.lines.first().quantity)
        assertEquals(Money.ofPounds(600), vm.state.value.totals.total)

        vm.onEvent(TillUiEvent.RemoveLine(0))
        assertTrue(vm.state.value.basket.isEmpty)
        assertEquals(Money.ZERO, vm.state.value.totals.total)
    }

    @Test
    fun `tender flow and sale completion clears basket and records sale`() = runTest(scheduler) {
        val vm = createViewModel()
        val effects = mutableListOf<TillUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(TillUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(TillUiEvent.Scanned("KSW-TSH-001"))
        advanceUntilIdle()

        vm.onEvent(TillUiEvent.StartTender)
        assertTrue(vm.state.value.isTendering)

        vm.onEvent(TillUiEvent.AddTender(TenderMethod.CASH, "200.00", "200.00"))
        assertEquals(1, vm.state.value.tenders.size)

        vm.onEvent(TillUiEvent.Complete)
        advanceUntilIdle()

        assertEquals(1, sales.recorded.size)
        assertEquals(Money.ofPounds(200), sales.recorded.first().total)
        assertTrue(vm.state.value.basket.isEmpty)
        assertFalse(vm.state.value.isTendering)
        assertTrue(effects.any { it is TillUiEffect.ShowMessage })
        job.cancel()
    }

    @Test
    fun `parking basket holds sale and resume restores it`() = runTest(scheduler) {
        val vm = createViewModel()
        val effects = mutableListOf<TillUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(TillUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(TillUiEvent.Scanned("KSW-TSH-001"))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.basket.lines.size)

        vm.onEvent(TillUiEvent.Hold("Customer stepped out"))
        advanceUntilIdle()

        assertTrue(vm.state.value.basket.isEmpty)
        assertEquals(1, held.held.size)
        val heldId = held.held.keys.first()

        vm.onEvent(TillUiEvent.Resume(heldId))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.basket.lines.size)
        assertEquals("var-1", vm.state.value.basket.lines.first().variantId)
        assertTrue(held.held.isEmpty())
        job.cancel()
    }
}
