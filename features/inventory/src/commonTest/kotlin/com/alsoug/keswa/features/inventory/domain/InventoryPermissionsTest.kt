package com.alsoug.keswa.features.inventory.domain

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IStockAdjustmentRepository
import com.alsoug.keswa.core.domain.repository.IStockCountRepository
import com.alsoug.keswa.core.domain.repository.IStockReceiptRepository
import com.alsoug.keswa.core.domain.repository.PostedReceipt
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.AdjustStockUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.CountVariantUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartCountUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartReceiptUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Permission checked in the domain, with the UI bypassed entirely — and checked *before* anything
 * is written, which is what these exploding fakes prove.
 *
 * The split that matters: a seller counts stock but does not receive it or write it off. Counting
 * is what the person on the shop floor does; deciding that three shirts no longer exist is not.
 */
class InventoryPermissionsTest {

    /** Records that something reached the data layer, which at that point is already a failure. */
    private class Tripwire {
        var touched = false

        fun boom(): Nothing {
            touched = true
            error("the repository should never have been reached")
        }
    }

    private class ExplodingReceipts(private val tripwire: Tripwire) : IStockReceiptRepository {
        override suspend fun createDraft(
            id: String,
            reference: String,
            supplierName: String,
            locationId: String,
            userId: String,
            atMillis: Long,
        ): Result<StockReceipt> = tripwire.boom()

        override suspend fun putLine(
            id: String,
            receiptId: String,
            variantId: String,
            quantity: Int,
            unitCost: Money,
        ): Result<StockReceipt> = tripwire.boom()

        override suspend fun removeLine(receiptId: String, lineId: String): Result<StockReceipt> =
            tripwire.boom()

        override suspend fun post(
            receiptId: String,
            userId: String,
            atMillis: Long,
        ): Result<PostedReceipt> = tripwire.boom()

        override suspend fun getById(id: String): Result<StockReceipt?> = tripwire.boom()

        override suspend fun discardDraft(id: String): Result<Unit> = tripwire.boom()

        override suspend fun recent(locationId: String, limit: Int): Result<List<StockReceipt>> =
            tripwire.boom()

        override fun observeDrafts(locationId: String): Flow<List<StockReceipt>> = flowOf(emptyList())
    }

    private class ExplodingCounts(private val tripwire: Tripwire) : IStockCountRepository {
        override suspend fun start(
            id: String,
            locationId: String,
            userId: String,
            atMillis: Long,
        ): Result<StockCount> = tripwire.boom()

        override suspend fun putLine(
            id: String,
            countId: String,
            variantId: String,
            counted: Int,
        ): Result<StockCount> = tripwire.boom()

        override suspend fun post(
            countId: String,
            userId: String,
            note: String?,
            atMillis: Long,
        ): Result<StockCount> = tripwire.boom()

        override suspend fun getById(id: String): Result<StockCount?> = tripwire.boom()

        override suspend fun current(locationId: String): Result<StockCount?> = tripwire.boom()

        override suspend fun discard(id: String): Result<Unit> = tripwire.boom()

        override suspend fun recent(locationId: String, limit: Int): Result<List<StockCount>> =
            tripwire.boom()
    }

    private class ExplodingAdjustments(private val tripwire: Tripwire) :
        IStockAdjustmentRepository {
        override suspend fun adjust(
            id: String,
            variantId: String,
            locationId: String,
            quantity: Int,
            reason: MovementReason,
            note: String,
            userId: String,
            atMillis: Long,
        ): Result<StockMovement> = tripwire.boom()

        override suspend fun historyFor(
            variantId: String,
            locationId: String,
        ): Result<List<StockMovement>> = tripwire.boom()
    }

    private class Ids : IdGenerator {
        override fun newId(): String = "id-1"
    }

    private val tripwire = Tripwire()
    private val receipts = ExplodingReceipts(tripwire)
    private val counts = ExplodingCounts(tripwire)
    private val adjustments = ExplodingAdjustments(tripwire)

    private fun session(role: UserRole?): ISessionManager = InMemorySessionManager().apply {
        role?.let { signIn(User("usr-1", "sara", "Sara", "سارة", it), atMillis = 0) }
    }

    @Test
    fun `a seller cannot start a delivery`() = runTest {
        val start = StartReceiptUseCase(receipts, session(UserRole.SELLER), Ids()) { 0 }

        assertIs<Error.ForbiddenAccess>(start("INV-1", "Nile", "loc-shop").exceptionOrNull())
        assertTrue(!tripwire.touched, "the check must come before anything is written")
    }

    @Test
    fun `a seller cannot add to one`() = runTest {
        val add = AddReceiptLineUseCase(receipts, session(UserRole.SELLER), Ids())

        assertIs<Error.ForbiddenAccess>(
            add("rec-1", "var-1", 10, Money.ofPounds(120)).exceptionOrNull(),
        )
        assertTrue(!tripwire.touched)
    }

    @Test
    fun `a seller cannot post one`() = runTest {
        val post = PostReceiptUseCase(receipts, session(UserRole.SELLER)) { 0 }

        assertIs<Error.ForbiddenAccess>(post("rec-1").exceptionOrNull())
        assertTrue(!tripwire.touched)
    }

    @Test
    fun `a seller cannot write stock off by hand`() = runTest {
        val adjust = AdjustStockUseCase(adjustments, session(UserRole.SELLER), Ids()) { 0 }

        assertIs<Error.ForbiddenAccess>(
            adjust("var-1", "loc-shop", -3, MovementReason.DAMAGE, "water damage").exceptionOrNull(),
        )
        assertTrue(!tripwire.touched)
    }

    @Test
    fun `nobody does any of it without a session`() = runTest {
        val start = StartReceiptUseCase(receipts, session(null), Ids()) { 0 }
        val count = StartCountUseCase(counts, session(null), Ids()) { 0 }

        assertIs<Error.ForbiddenAccess>(start("INV-1", "Nile", "loc-shop").exceptionOrNull())
        assertIs<Error.ForbiddenAccess>(count("loc-shop").exceptionOrNull())
        assertTrue(!tripwire.touched)
    }

    @Test
    fun `a seller can count, because counting is what the shop floor does`() = runTest {
        val start = StartCountUseCase(counts, session(UserRole.SELLER), Ids()) { 0 }
        val line = CountVariantUseCase(counts, session(UserRole.SELLER), Ids())

        // Permitted, so it reaches the repository — which then explodes, proving it got past the
        // check rather than being refused by it.
        assertTrue(start("loc-shop").isFailure)
        assertTrue(tripwire.touched)
        assertTrue(line("cnt-1", "var-1", 9).isFailure)
    }

    @Test
    fun `a hand adjustment must be an adjustment or damage, not a disguised sale`() = runTest {
        val adjust = AdjustStockUseCase(adjustments, session(UserRole.ADMIN), Ids()) { 0 }

        val thrown = adjust("var-1", "loc-shop", -3, MovementReason.SALE, "oops").exceptionOrNull()

        assertIs<IllegalArgumentException>(thrown)
        assertEquals(false, tripwire.touched)
    }
}
