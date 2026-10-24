package com.alsoug.keswa.features.returns.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Location
import com.alsoug.keswa.core.domain.model.LocationType
import com.alsoug.keswa.core.domain.model.ReturnCondition
import com.alsoug.keswa.core.domain.model.ReturnableLine
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleReturn
import com.alsoug.keswa.core.domain.model.SaleReturnLine
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.SecretKind
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ILocationRepository
import com.alsoug.keswa.core.domain.repository.ISaleRepository
import com.alsoug.keswa.core.domain.repository.ISaleReturnRepository
import com.alsoug.keswa.core.domain.repository.ISettingsRepository
import com.alsoug.keswa.core.domain.repository.IUserRepository
import com.alsoug.keswa.core.domain.repository.ReturnDraft
import com.alsoug.keswa.core.domain.repository.SaleDraft
import com.alsoug.keswa.core.domain.repository.StoredCredential
import com.alsoug.keswa.core.platform.IPasswordHasher
import com.alsoug.keswa.core.platform.IPrinterTransport
import com.alsoug.keswa.core.platform.IReceiptRenderer
import com.alsoug.keswa.core.printing.MonoBitmap
import com.alsoug.keswa.core.printing.model.Receipt
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.core.session.LockoutAwareCredentialVerifier
import com.alsoug.keswa.features.returns.domain.usecase.CompleteReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.FindSaleForReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.PrintRefundNoteUseCase
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsUiEffect
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsUiEvent
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsViewModel
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

private class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var next = 0
    override fun newId(): String = "$prefix-${++next}"
}

private class FakeHasher : IPasswordHasher {
    override suspend fun hash(secret: CharArray, salt: ByteArray): String =
        "hash(${secret.concatToString()}:${salt.joinToString("")})"

    override suspend fun verify(secret: CharArray, salt: ByteArray, expected: String): Boolean =
        hash(secret, salt) == expected

    override fun newSalt(): ByteArray = ByteArray(4) { 1 }
}

private class FakeSettings(var shop: ShopSettings = ShopSettings(returnWindowDays = 14)) : ISettingsRepository {
    override suspend fun get(): Result<ShopSettings> = Result.success(shop)
    override suspend fun save(settings: ShopSettings): Result<Unit> {
        shop = settings
        return Result.success(Unit)
    }
}

private class FakeReturnsSales(val sales: MutableMap<String, Sale> = mutableMapOf()) : ISaleRepository {
    override suspend fun record(draft: SaleDraft): Result<Sale> = error("unused")
    override suspend fun void(saleId: String, byUserId: String, reason: String, atMillis: Long): Result<Sale> = error("unused")
    override suspend fun getById(id: String): Result<Sale?> = Result.success(sales[id])
    override suspend fun getByReceiptNumber(receiptNumber: Long): Result<Sale?> =
        Result.success(sales.values.firstOrNull { it.receiptNumber == receiptNumber })
    override fun observeRecent(limit: Int): Flow<List<Sale>> = flowOf(sales.values.toList())
}

private class FakeSaleReturns : ISaleReturnRepository {
    val recorded = mutableListOf<ReturnDraft>()
    var linesToReturn: List<ReturnableLine> = emptyList()

    override suspend fun record(draft: ReturnDraft): Result<SaleReturn> {
        recorded += draft
        return Result.success(
            SaleReturn(
                id = draft.id,
                returnNumber = 1001L,
                originalSaleId = draft.originalSaleId,
                locationId = draft.locationId,
                userId = draft.userId,
                shiftId = draft.shiftId,
                status = SaleStatus.COMPLETED,
                reason = draft.reason,
                refundMethod = draft.refundMethod,
                refundAmount = draft.refundAmount,
                subtotal = draft.subtotal,
                tax = draft.tax,
                occurredAt = draft.occurredAt,
                authorisedByUserId = draft.authorisedByUserId,
                lines = draft.lines,
            ),
        )
    }

    override suspend fun void(returnId: String, byUserId: String, reason: String, atMillis: Long): Result<SaleReturn> = error("unused")
    override suspend fun linkExchange(returnId: String, saleId: String): Result<Unit> = Result.success(Unit)
    override suspend fun getById(id: String): Result<SaleReturn?> = Result.success(null)
    override suspend fun getByNumber(returnNumber: Long): Result<SaleReturn?> = Result.success(null)
    override suspend fun returnableLines(saleId: String): Result<List<ReturnableLine>> = Result.success(linesToReturn)
    override suspend fun lowestSoldPrice(variantId: String): Result<Money?> = Result.success(Money.ofPounds(120))
    override fun observeRecent(limit: Int): Flow<List<SaleReturn>> = flowOf(emptyList())
}

private class FakeUsers : IUserRepository {
    val stored = mutableMapOf<String, StoredCredential>()

    override suspend fun create(
        id: String,
        username: String,
        displayName: String,
        displayNameAr: String,
        role: UserRole,
        secretKind: SecretKind,
        secretHash: String,
        secretSalt: String,
        mustChangeSecret: Boolean,
    ): Result<User> {
        val user = User(id, username.lowercase(), displayName, displayNameAr, role, mustChangeSecret)
        stored[id] = StoredCredential(user, secretHash, secretSalt, secretKind, 0, null)
        return Result.success(user)
    }

    override suspend fun findByUsername(username: String): Result<StoredCredential?> =
        Result.success(stored.values.firstOrNull { it.user.username == username.lowercase() })

    override suspend fun findById(id: String): Result<StoredCredential?> = Result.success(stored[id])
    override suspend fun sellers(): Result<List<User>> = Result.success(emptyList())
    override suspend fun countActive(): Result<Int> = Result.success(stored.size)
    override suspend fun countActiveAdmins(): Result<Int> = Result.success(1)
    override suspend fun recordFailure(userId: String, attempts: Int, lockedUntil: Long?): Result<Unit> = Result.success(Unit)
    override suspend fun clearFailures(userId: String): Result<Unit> = Result.success(Unit)
    override suspend fun replaceSecret(userId: String, secretHash: String, secretSalt: String, mustChangeSecret: Boolean): Result<Unit> = Result.success(Unit)
}

private class DummyRenderer : IReceiptRenderer {
    override fun render(receipt: Receipt, widthDots: Int) = MonoBitmap(widthDots, 4)
    override fun renderTestPage(widthDots: Int) = MonoBitmap(widthDots, 4)
}

private class DummyTransport : IPrinterTransport {
    override suspend fun open() = Result.success(Unit)
    override suspend fun write(bytes: ByteArray) = Result.success(Unit)
    override suspend fun close() = Unit
}

private class DummyLocations : ILocationRepository {
    override suspend fun default() = Result.success(Location("loc-1", "Shop", "محل", LocationType.SHOP, isDefault = true, isActive = true))
    override suspend fun ensureDefault(id: String, name: String, nameAr: String) = default()
    override suspend fun getAll(): Result<List<Location>> = Result.success(emptyList())
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReturnsViewModelTest {

    private val scheduler = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private val now = { 1_757_000_000_000L }
    private val sales = FakeReturnsSales()
    private val returns = FakeSaleReturns()
    private val settings = FakeSettings()
    private val users = FakeUsers()
    private val sessions = InMemorySessionManager()
    private val hasher = FakeHasher()

    private val seller = User("u-seller", "sara", "Sara", "سارة", UserRole.SELLER)

    private val locations = DummyLocations()

    private fun createViewModel() = ReturnsViewModel(
        findSale = FindSaleForReturnUseCase(sales, returns, settings, now),
        completeReturn = CompleteReturnUseCase(
            returns = returns,
            sessions = sessions,
            users = users,
            locations = locations,
            settings = settings,
            ids = SequentialIds("ret"),
            now = now,
        ),
        printRefundNote = PrintRefundNoteUseCase(
            settings = settings,
            renderer = DummyRenderer(),
        ) { _, _ -> DummyTransport() },
        verifier = LockoutAwareCredentialVerifier(users, hasher, now),
        settings = settings,
        dispatchers = dispatchers,
    )

    private fun testSale(receiptNumber: Long = 42L, occurredAt: Long = now()) = Sale(
        id = "sale-1",
        receiptNumber = receiptNumber,
        locationId = "loc-1",
        priceListId = "pl-1",
        userId = "u-seller",
        shiftId = "shift-1",
        status = SaleStatus.COMPLETED,
        subtotal = Money.ofPounds(300),
        discount = Money.ZERO,
        tax = Money.ZERO,
        total = Money.ofPounds(300),
        tendered = Money.ofPounds(300),
        change = Money.ZERO,
        occurredAt = occurredAt,
    )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(scheduler)
        sessions.signIn(seller, now())
        returns.linesToReturn = listOf(
            ReturnableLine(
                saleLineId = "sl-1",
                variantId = "var-1",
                description = "Classic Linen Shirt",
                soldQuantity = 2,
                alreadyReturned = 0,
                unitPrice = Money.ofPounds(150),
                unitCost = Money.ofPounds(100),
            ),
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `load fetches return policy window into state`() = runTest(scheduler) {
        val vm = createViewModel()
        vm.onEvent(ReturnsUiEvent.Load)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals(14, vm.state.value.returnWindowDays)
    }

    @Test
    fun `lookup by valid receipt number populates sale and returnable lines`() = runTest(scheduler) {
        sales.sales["sale-1"] = testSale(receiptNumber = 42L)

        val vm = createViewModel()
        vm.onEvent(ReturnsUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ReturnsUiEvent.LookupEntryChanged("42"))
        vm.onEvent(ReturnsUiEvent.Lookup)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("sale-1", state.saleId)
        assertEquals(42L, state.receiptNumber)
        assertTrue(state.isInsidePolicy)
        assertEquals(1, state.lines.size)
        assertEquals(2, state.lines.first().returnable)
        assertEquals(0, state.lines.first().selectedQuantity)
    }

    @Test
    fun `lookup by unknown receipt emits error effect`() = runTest(scheduler) {
        val vm = createViewModel()
        val effects = mutableListOf<ReturnsUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(ReturnsUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ReturnsUiEvent.LookupEntryChanged("999"))
        vm.onEvent(ReturnsUiEvent.Lookup)
        advanceUntilIdle()

        assertEquals(1, effects.size)
        assertIs<ReturnsUiEffect.ShowError>(effects.first())
        assertNull(vm.state.value.saleId)
        job.cancel()
    }

    @Test
    fun `quantity change and condition updates lines in state`() = runTest(scheduler) {
        sales.sales["sale-1"] = testSale()

        val vm = createViewModel()
        vm.onEvent(ReturnsUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ReturnsUiEvent.LookupEntryChanged("42"))
        vm.onEvent(ReturnsUiEvent.Lookup)
        advanceUntilIdle()

        vm.onEvent(ReturnsUiEvent.QuantityChanged("sl-1", 1))
        assertEquals(1, vm.state.value.lines.first().selectedQuantity)

        vm.onEvent(ReturnsUiEvent.ConditionChanged("sl-1", ReturnCondition.DAMAGED))
        assertEquals(ReturnCondition.DAMAGED, vm.state.value.lines.first().condition)

        vm.onEvent(ReturnsUiEvent.ReasonChanged("Defective stitching"))
        assertEquals("Defective stitching", vm.state.value.reason)
    }

    @Test
    fun `complete return commits return draft and resets form`() = runTest(scheduler) {
        sales.sales["sale-1"] = testSale()

        val vm = createViewModel()
        val effects = mutableListOf<ReturnsUiEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        runCurrent()

        vm.onEvent(ReturnsUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ReturnsUiEvent.LookupEntryChanged("42"))
        vm.onEvent(ReturnsUiEvent.Lookup)
        advanceUntilIdle()

        vm.onEvent(ReturnsUiEvent.QuantityChanged("sl-1", 1))
        vm.onEvent(ReturnsUiEvent.ReasonChanged("Wrong size"))
        vm.onEvent(ReturnsUiEvent.Complete)
        advanceUntilIdle()

        assertEquals(1, returns.recorded.size)
        val returnDraft = returns.recorded.first()
        assertEquals("sale-1", returnDraft.originalSaleId)
        assertEquals(Money.ofPounds(150), returnDraft.refundAmount)

        assertTrue(effects.any { it is ReturnsUiEffect.ShowMessage })
        job.cancel()
    }
}
