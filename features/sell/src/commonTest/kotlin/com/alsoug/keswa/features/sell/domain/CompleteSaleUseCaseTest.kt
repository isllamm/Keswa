package com.alsoug.keswa.features.sell.domain

import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import com.alsoug.keswa.features.sell.domain.usecase.CompleteSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.SaleResult
import com.alsoug.keswa.features.sell.domain.usecase.SaleWarning
import com.alsoug.keswa.features.sell.domain.usecase.Tender
import com.alsoug.keswa.features.sell.domain.usecase.TillContext
import com.alsoug.keswa.features.sell.domain.usecase.VoidSaleUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Permission enforced in the domain, with the UI bypassed entirely.
 *
 * It is easy to ship a build where the only thing between a seller and a markdown is a hidden
 * button — and on an offline desktop app, the user owns the machine the UI runs on.
 */
class CompleteSaleUseCaseTest {

    private val sales = RecordingSaleRepository()
    private val users = FakeUserRepository()
    private val sessions = InMemorySessionManager()
    private val till = TillContext(locationId = "loc-shop", priceListId = "pricelist-retail")

    private val complete = CompleteSaleUseCase(
        sales = sales,
        sessions = sessions,
        users = users,
        // No customer and no balance: these are retail sales, which is Phase 5's whole subject.
        customers = FakeCustomers(null),
        receivables = FakeReceivables(Money.ZERO),
        calculate = CalculateBasketTotalUseCase(),
        ids = SequentialIds("sale"),
        now = { 1_757_000_000_000 },
    )

    private val seller = User("usr-seller", "sara", "Sara", "سارة", UserRole.SELLER)
    private val admin = User("usr-admin", "owner", "Owner", "المالك", UserRole.ADMIN)

    private fun signInSeller() = sessions.signIn(seller, 1_757_000_000_000)

    private fun signInAdmin() = sessions.signIn(admin, 1_757_000_000_000)

    private fun basket(onHand: Int = 10) = Basket().add(sellable(onHand = onHand), quantity = 2)

    private fun cash(amount: Long, handedOver: Long = amount) =
        listOf(Tender(TenderMethod.CASH, Money.ofPounds(amount), Money.ofPounds(handedOver)))

    @Test
    fun `a seller can sell`() = runTest {
        signInSeller()

        val result = complete(basket(), cash(360), till, shiftId = "shift-1", vatBasisPoints = 0)
            .getOrThrow()

        val completed = assertIs<SaleResult.Completed>(result)
        assertEquals(Money.ofPounds(360), completed.sale.total)
        assertEquals("usr-seller", sales.recorded.single().userId)
        assertEquals("shift-1", sales.recorded.single().shiftId)
    }

    @Test
    fun `nobody sells without a session`() = runTest {
        val thrown = complete(basket(), cash(360), till, null, 0).exceptionOrNull()

        assertIs<Error.ForbiddenAccess>(thrown)
        assertTrue(sales.recorded.isEmpty())
    }

    @Test
    fun `a seller cannot discount a line on their own authority`() = runTest {
        signInSeller()
        val discounted = basket().withLineDiscount(0, Money.ofPounds(40), authorisedByUserId = null)

        val thrown = complete(discounted, cash(320), till, null, 0).exceptionOrNull()

        assertIs<Error.ForbiddenAccess>(thrown)
        assertTrue(sales.recorded.isEmpty(), "nothing may reach the ledger")
    }

    @Test
    fun `a seller cannot override a price on their own authority`() = runTest {
        signInSeller()
        val overridden = basket().withUnitPrice(0, Money.ofPounds(150), authorisedByUserId = null)

        assertIs<Error.ForbiddenAccess>(complete(overridden, cash(300), till, null, 0).exceptionOrNull())
    }

    @Test
    fun `an admin needs no second approval`() = runTest {
        signInAdmin()
        val discounted = basket().withLineDiscount(0, Money.ofPounds(40), authorisedByUserId = null)

        assertIs<SaleResult.Completed>(complete(discounted, cash(320), till, null, 0).getOrThrow())
    }

    @Test
    fun `a seller may discount once an admin has approved it`() = runTest {
        signInSeller()
        users.add("usr-admin", "owner", UserRole.ADMIN, "s3cret")
        val discounted = basket().withLineDiscount(0, Money.ofPounds(40), authorisedByUserId = "usr-admin")

        val result = complete(discounted, cash(320), till, null, 0).getOrThrow()

        assertIs<SaleResult.Completed>(result)
        // The approval travels onto the line, because "who authorised this" is the first thing an
        // owner asks about a marked-down receipt.
        assertEquals("usr-admin", sales.recorded.single().lines.single().authorisedByUserId)
    }

    @Test
    fun `an approval naming someone without the permission is refused`() = runTest {
        signInSeller()
        users.add("usr-other", "mo", UserRole.SELLER, "1234")
        val discounted = basket().withLineDiscount(0, Money.ofPounds(40), authorisedByUserId = "usr-other")

        // The recorded id is a claim the screen makes; the use case looks it up rather than
        // trusting it, so a UI bug cannot smuggle an unapproved discount into the ledger.
        assertIs<Error.ForbiddenAccess>(complete(discounted, cash(320), till, null, 0).exceptionOrNull())
    }

    @Test
    fun `an approval naming nobody at all is refused`() = runTest {
        signInSeller()
        val discounted = basket().withLineDiscount(0, Money.ofPounds(40), authorisedByUserId = "ghost")

        assertIs<Error.ForbiddenAccess>(complete(discounted, cash(320), till, null, 0).exceptionOrNull())
    }

    @Test
    fun `an under-tendered sale is refused rather than committed`() = runTest {
        signInSeller()

        val result = complete(basket(), cash(300), till, null, 0).getOrThrow()

        val short = assertIs<SaleResult.UnderTendered>(result)
        assertEquals(Money.ofPounds(60), short.shortBy)
        assertTrue(sales.recorded.isEmpty())
    }

    @Test
    fun `over-tendered cash becomes change`() = runTest {
        signInSeller()

        val result = complete(basket(), cash(360, handedOver = 500), till, null, 0).getOrThrow()

        assertIs<SaleResult.Completed>(result)
        assertEquals(Money.ofPounds(140), sales.recorded.single().change)
        assertEquals(Money.ofPounds(500), sales.recorded.single().tendered)
    }

    @Test
    fun `a split tender settles the sale`() = runTest {
        signInSeller()
        val tenders = listOf(
            Tender(TenderMethod.CASH, Money.ofPounds(200), Money.ofPounds(200)),
            Tender(TenderMethod.CARD, Money.ofPounds(160), Money.ofPounds(160), reference = "APPROVED"),
        )

        val result = complete(basket(), tenders, till, null, 0).getOrThrow()

        assertIs<SaleResult.Completed>(result)
        assertEquals(2, sales.recorded.single().payments.size)
        assertEquals(Money.ZERO, sales.recorded.single().change)
    }

    @Test
    fun `an empty basket is nothing to sell`() = runTest {
        signInSeller()

        assertIs<SaleResult.EmptyBasket>(complete(Basket(), cash(0), till, null, 0).getOrThrow())
    }

    @Test
    fun `selling below stock completes and warns`() = runTest {
        signInSeller()

        val result = complete(basket(onHand = 1), cash(360), till, null, 0).getOrThrow()

        val completed = assertIs<SaleResult.Completed>(result)
        val warning = assertIs<SaleWarning.SoldBelowStock>(completed.warnings.single())
        assertEquals(1, warning.onHand)
        assertEquals(2, warning.sold)
        // A signal, not a gate: the figure is more often wrong than the customer's hands.
        assertEquals(1, sales.recorded.size)
    }

    @Test
    fun `the line snapshots cost, so margin does not move when the supplier's price does`() = runTest {
        signInSeller()

        complete(basket(), cash(360), till, null, 0).getOrThrow()

        assertEquals(Money.ofPounds(120), sales.recorded.single().lines.single().unitCost)
    }

    @Test
    fun `a void needs the approver's permission, not the session's`() = runTest {
        val void = VoidSaleUseCase(sales, now = { 1_757_000_100_000 })
        signInSeller()
        complete(basket(), cash(360), till, null, 0).getOrThrow()
        val saleId = sales.recorded.single().id

        assertIs<Error.ForbiddenAccess>(void(saleId, "wrong item", seller).exceptionOrNull())
        assertTrue(sales.voided.isEmpty())

        void(saleId, "wrong item", admin).getOrThrow()
        assertEquals(listOf(Triple(saleId, "usr-admin", "wrong item")), sales.voided)
    }

    @Test
    fun `a void without a reason is refused`() = runTest {
        val void = VoidSaleUseCase(sales, now = { 1_757_000_100_000 })
        signInAdmin()
        complete(basket(), cash(360), till, null, 0).getOrThrow()

        assertIs<Error.InvalidData>(void(sales.recorded.single().id, "   ", admin).exceptionOrNull())
    }
}
