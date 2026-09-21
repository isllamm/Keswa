package com.alsoug.keswa.features.returns.domain

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Location
import com.alsoug.keswa.core.domain.model.LocationType
import com.alsoug.keswa.core.domain.model.ReturnableLine
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleReturn
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
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.returns.domain.usecase.CompleteReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.FindSaleForReturnUseCase
import com.alsoug.keswa.features.returns.domain.usecase.ReturnResult
import com.alsoug.keswa.features.returns.domain.usecase.ReturningLine
import com.alsoug.keswa.features.returns.domain.usecase.SaleLookup
import com.alsoug.keswa.features.returns.domain.usecase.VoidReturnUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The policy, enforced in the domain with the UI bypassed.
 *
 * Two of these cost real money when they are wrong: over-returning, and letting a seller take
 * goods back long after the window on their own say-so.
 */
class ReturnPolicyTest {

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val SOLD_AT = 1_757_000_000_000L
    }

    private var now = SOLD_AT

    private class Ids : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }

    private class FakeSales(private val sale: Sale?) : ISaleRepository {
        override suspend fun record(draft: SaleDraft): Result<Sale> = error("not used")
        override suspend fun void(
            saleId: String,
            byUserId: String,
            reason: String,
            atMillis: Long,
        ): Result<Sale> = error("not used")

        override suspend fun getById(id: String): Result<Sale?> =
            Result.success(sale?.takeIf { it.id == id })

        override suspend fun getByReceiptNumber(receiptNumber: Long): Result<Sale?> =
            Result.success(sale?.takeIf { it.receiptNumber == receiptNumber })

        override fun observeRecent(limit: Int): Flow<List<Sale>> = flowOf(emptyList())
    }

    private class FakeReturns(
        var lines: List<ReturnableLine> = emptyList(),
    ) : ISaleReturnRepository {
        val recorded = mutableListOf<ReturnDraft>()

        override suspend fun record(draft: ReturnDraft): Result<SaleReturn> {
            recorded += draft
            return Result.success(
                SaleReturn(
                    id = draft.id,
                    returnNumber = recorded.size.toLong(),
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

        override suspend fun void(
            returnId: String,
            byUserId: String,
            reason: String,
            atMillis: Long,
        ): Result<SaleReturn> = error("not used")

        override suspend fun linkExchange(returnId: String, saleId: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun getById(id: String): Result<SaleReturn?> = Result.success(null)

        override suspend fun getByNumber(returnNumber: Long): Result<SaleReturn?> =
            Result.success(null)

        override suspend fun returnableLines(saleId: String): Result<List<ReturnableLine>> =
            Result.success(lines)

        override suspend fun lowestSoldPrice(variantId: String): Result<Money?> =
            Result.success(Money.ofPounds(120))

        override fun observeRecent(limit: Int): Flow<List<SaleReturn>> = flowOf(emptyList())
    }

    private class FakeLocations : ILocationRepository {
        override suspend fun default(): Result<Location?> = Result.success(
            Location("loc-shop", "Shop", "المحل", LocationType.SHOP, isDefault = true, isActive = true),
        )

        override suspend fun ensureDefault(
            id: String,
            name: String,
            nameAr: String,
        ): Result<Location> = error("not used")

        override suspend fun getAll(): Result<List<Location>> = Result.success(emptyList())
    }

    private class FakeSettings(private val settings: ShopSettings) : ISettingsRepository {
        override suspend fun get(): Result<ShopSettings> = Result.success(settings)
        override suspend fun save(settings: ShopSettings): Result<Unit> = Result.success(Unit)
    }

    private class FakeUsers : IUserRepository {
        val stored = mutableMapOf<String, User>()

        fun add(id: String, role: UserRole) {
            stored[id] = User(id, id, id, id, role)
        }

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
        ): Result<User> = error("not used")

        override suspend fun findByUsername(username: String): Result<StoredCredential?> =
            Result.success(null)

        override suspend fun findById(id: String): Result<StoredCredential?> = Result.success(
            stored[id]?.let { StoredCredential(it, "", "", SecretKind.PASSWORD, 0, null) },
        )

        override suspend fun sellers(): Result<List<User>> = Result.success(emptyList())
        override suspend fun countActive(): Result<Int> = Result.success(stored.size)
        override suspend fun countActiveAdmins(): Result<Int> = Result.success(1)
        override suspend fun recordFailure(
            userId: String,
            attempts: Int,
            lockedUntil: Long?,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun clearFailures(userId: String): Result<Unit> = Result.success(Unit)
        override suspend fun replaceSecret(
            userId: String,
            secretHash: String,
            secretSalt: String,
            mustChangeSecret: Boolean,
        ): Result<Unit> = error("not used")
    }

    private val sale = Sale(
        id = "sale-1",
        receiptNumber = 412,
        locationId = "loc-shop",
        priceListId = "pricelist-retail",
        userId = "usr-seller",
        shiftId = "shift-1",
        status = SaleStatus.COMPLETED,
        subtotal = Money.ofPounds(540),
        discount = Money.ZERO,
        tax = Money.ZERO,
        total = Money.ofPounds(540),
        tendered = Money.ofPounds(540),
        change = Money.ZERO,
        occurredAt = SOLD_AT,
    )

    private val returnable = ReturnableLine(
        saleLineId = "sl-1",
        variantId = "var-1",
        description = "Round-neck t-shirt — Navy",
        soldQuantity = 3,
        alreadyReturned = 1,
        unitPrice = Money.ofPounds(180),
        unitCost = Money.ofPounds(120),
    )

    private val returns = FakeReturns(listOf(returnable))
    private val users = FakeUsers()
    private val sessions = InMemorySessionManager()

    private val seller = User("usr-seller", "sara", "Sara", "سارة", UserRole.SELLER)
    private val admin = User("usr-admin", "owner", "Owner", "المالك", UserRole.ADMIN)

    private fun find(windowDays: Int = 14) = FindSaleForReturnUseCase(
        sales = FakeSales(sale),
        returns = returns,
        settings = FakeSettings(ShopSettings(returnWindowDays = windowDays)),
        now = { now },
    )

    private fun complete() = CompleteReturnUseCase(
        returns = returns,
        sessions = sessions,
        users = users,
        locations = FakeLocations(),
        settings = FakeSettings(ShopSettings()),
        ids = Ids(),
        now = { now },
    )

    private fun line(quantity: Int) = ReturningLine(
        saleLineId = "sl-1",
        variantId = "var-1",
        description = "Round-neck t-shirt — Navy",
        quantity = quantity,
        unitRefund = Money.ofPounds(180),
        unitCost = Money.ofPounds(120),
    )

    @Test
    fun `a receipt inside the window is returnable by a seller`() = runTest {
        now = SOLD_AT + 3 * DAY
        sessions.signIn(seller, now)

        val found = assertIs<SaleLookup.Found>(find().byReceiptNumber(412).getOrThrow())
        assertTrue(found.isInsidePolicy)
        assertEquals(3, found.daysSince)

        val result = complete()(
            originalSaleId = "sale-1",
            lines = listOf(line(1)),
            reason = "wrong size",
            refundMethod = TenderMethod.CASH,
            shiftId = "shift-1",
            authorisedByUserId = null,
            needsAuthority = found.needsApproval(),
        ).getOrThrow()

        assertIs<ReturnResult.Completed>(result)
    }

    @Test
    fun `outside the window a seller is refused`() = runTest {
        now = SOLD_AT + 40 * DAY
        sessions.signIn(seller, now)

        val found = assertIs<SaleLookup.Found>(find().byReceiptNumber(412).getOrThrow())
        assertTrue(!found.isInsidePolicy)

        // The permission is the policy: the seller cannot wave it through alone.
        val thrown = complete()(
            originalSaleId = "sale-1",
            lines = listOf(line(1)),
            reason = "changed my mind",
            refundMethod = TenderMethod.CASH,
            shiftId = null,
            authorisedByUserId = null,
            needsAuthority = true,
        ).exceptionOrNull()

        assertIs<Error.ForbiddenAccess>(thrown)
        assertTrue(returns.recorded.isEmpty(), "nothing may reach the ledger")
    }

    @Test
    fun `outside the window an admin approval lets it through, and is recorded`() = runTest {
        now = SOLD_AT + 40 * DAY
        sessions.signIn(seller, now)
        users.add("usr-admin", UserRole.ADMIN)

        val result = complete()(
            originalSaleId = "sale-1",
            lines = listOf(line(1)),
            reason = "faulty seam",
            refundMethod = TenderMethod.CASH,
            shiftId = null,
            authorisedByUserId = "usr-admin",
            needsAuthority = true,
        ).getOrThrow()

        assertIs<ReturnResult.Completed>(result)
        // Who said yes to this is the first thing an owner asks about a late refund.
        assertEquals("usr-admin", returns.recorded.single().authorisedByUserId)
    }

    @Test
    fun `an approval naming somebody without the permission is refused`() = runTest {
        now = SOLD_AT + 40 * DAY
        sessions.signIn(seller, now)
        users.add("usr-other", UserRole.SELLER)

        // The id in the field is a claim the screen makes; the use case looks it up.
        assertIs<Error.ForbiddenAccess>(
            complete()(
                originalSaleId = "sale-1",
                lines = listOf(line(1)),
                reason = "late",
                refundMethod = TenderMethod.CASH,
                shiftId = null,
                authorisedByUserId = "usr-other",
                needsAuthority = true,
            ).exceptionOrNull(),
        )
        assertTrue(returns.recorded.isEmpty())
    }

    @Test
    fun `returning more than is left is refused`() = runTest {
        now = SOLD_AT + DAY
        sessions.signIn(seller, now)

        // Three sold, one already back: two left, and three is not allowed.
        val result = complete()(
            originalSaleId = "sale-1",
            lines = listOf(line(3)),
            reason = "all of them",
            refundMethod = TenderMethod.CASH,
            shiftId = null,
            authorisedByUserId = null,
            needsAuthority = false,
        ).getOrThrow()

        val refused = assertIs<ReturnResult.ExceedsSold>(result)
        assertEquals(2, refused.returnable)
        assertTrue(returns.recorded.isEmpty())
    }

    @Test
    fun `an empty selection is nothing to return`() = runTest {
        sessions.signIn(seller, now)

        assertIs<ReturnResult.NothingToReturn>(
            complete()(
                originalSaleId = "sale-1",
                lines = emptyList(),
                reason = "",
                refundMethod = TenderMethod.CASH,
                shiftId = null,
                authorisedByUserId = null,
                needsAuthority = false,
            ).getOrThrow(),
        )
    }

    @Test
    fun `nobody returns anything without a session`() = runTest {
        assertIs<Error.ForbiddenAccess>(
            complete()(
                originalSaleId = "sale-1",
                lines = listOf(line(1)),
                reason = "wrong size",
                refundMethod = TenderMethod.CASH,
                shiftId = null,
                authorisedByUserId = null,
                needsAuthority = false,
            ).exceptionOrNull(),
        )
    }

    @Test
    fun `a fully returned receipt is refused before anyone picks a line`() = runTest {
        returns.lines = listOf(returnable.copy(alreadyReturned = 3))

        assertIs<SaleLookup.FullyReturned>(find().byReceiptNumber(412).getOrThrow())
    }

    @Test
    fun `a voided sale cannot be returned against`() = runTest {
        val voided = FindSaleForReturnUseCase(
            sales = FakeSales(sale.copy(status = SaleStatus.VOIDED)),
            returns = returns,
            settings = FakeSettings(ShopSettings()),
            now = { now },
        )

        // It was already reversed; returning against it would refund the same money twice.
        assertIs<SaleLookup.Voided>(voided.byReceiptNumber(412).getOrThrow())
    }

    @Test
    fun `an unknown receipt is simply not found`() = runTest {
        assertIs<SaleLookup.NotFound>(find().byReceiptNumber(999).getOrThrow())
        assertIs<SaleLookup.NotFound>(find().byQrCode("no-such-sale").getOrThrow())
    }

    @Test
    fun `voiding a refund needs the approver's permission, not the session's`() = runTest {
        val void = VoidReturnUseCase(returns) { now }

        assertIs<Error.ForbiddenAccess>(void("ret-1", "error", seller).exceptionOrNull())
        assertIs<Error.InvalidData>(void("ret-1", "   ", admin).exceptionOrNull())
    }
}

/** A plain in-policy return with a receipt is a seller's job; anything else needs an admin. */
private fun SaleLookup.Found.needsApproval(): Boolean = !isInsidePolicy
