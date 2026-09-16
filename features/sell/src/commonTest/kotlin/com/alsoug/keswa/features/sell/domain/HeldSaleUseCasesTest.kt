package com.alsoug.keswa.features.sell.domain

import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.usecase.DiscardHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.HoldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ListHeldSalesUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ResumeHeldSaleUseCase
import com.alsoug.keswa.features.sell.domain.usecase.TillContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * A customer goes to fetch another colour and there are four people behind them.
 *
 * The thing to get right is that a parked cart keeps the price that was *agreed*: coming back to a
 * higher number because a markdown ended while they were choosing is how a shop loses the sale and
 * the customer.
 */
class HeldSaleUseCasesTest {

    private val held = FakeHeldSaleRepository()
    private val sessions = InMemorySessionManager().apply {
        signIn(User("usr-seller", "sara", "Sara", "سارة", UserRole.SELLER), 1_757_000_000_000)
    }
    private val till = TillContext(locationId = "loc-shop", priceListId = "pricelist-retail")

    private val hold = HoldSaleUseCase(held, sessions, SequentialIds("held")) { 1_757_000_000_000 }
    private val list = ListHeldSalesUseCase(held)
    private val discard = DiscardHeldSaleUseCase(held)

    private fun resume(vararg available: com.alsoug.keswa.core.domain.model.SellableItem) =
        ResumeHeldSaleUseCase(held, FakeSellableRepository(available.toList())) { 1_757_000_100_000 }

    private fun basket() = Basket()
        .add(sellable(variantId = "var-1"), quantity = 2)
        .withLineDiscount(0, Money.ofPounds(40), authorisedByUserId = "usr-admin")

    @Test
    fun `holding empties nothing it was not given`() = runTest {
        assertTrue(hold(Basket(), "Ahmed", till).isFailure)
    }

    @Test
    fun `a parked cart comes back with the price that was agreed`() = runTest {
        val parked = hold(basket().withUnitPrice(0, Money.ofPounds(150), "usr-admin"), "Ahmed", till)
            .getOrThrow()

        // The list price has moved on since; the agreed one has not.
        val resumed = resume(sellable(variantId = "var-1", price = Money.ofPounds(200)))(parked.id, till)
            .getOrThrow()

        val line = resumed.basket.lines.single()
        assertEquals(Money.ofPounds(150), line.unitPrice)
        assertEquals(Money.ofPounds(40), line.lineDiscount)
        assertEquals("usr-admin", line.authorisedByUserId)
        // The list price *is* refreshed, so the screen can still say it was overridden.
        assertEquals(Money.ofPounds(200), line.listPrice)
    }

    @Test
    fun `stock is re-read on resume, because that is a fact about now`() = runTest {
        val parked = hold(basket(), "Ahmed", till).getOrThrow()

        val resumed = resume(sellable(variantId = "var-1", onHand = 1))(parked.id, till).getOrThrow()

        assertEquals(1, resumed.basket.lines.single().onHand)
        assertTrue(resumed.basket.sellsBelowStock)
    }

    @Test
    fun `a line whose variant has been retired is dropped and reported`() = runTest {
        val parked = hold(basket(), "Ahmed", till).getOrThrow()

        // Nothing sellable answers for var-1 any more.
        val resumed = resume()(parked.id, till).getOrThrow()

        assertTrue(resumed.basket.isEmpty)
        assertEquals(listOf("var-1"), resumed.dropped)
    }

    @Test
    fun `resuming takes the cart off the held list`() = runTest {
        val parked = hold(basket(), "Ahmed", till).getOrThrow()
        assertEquals(1, list(till).getOrThrow().size)

        resume(sellable(variantId = "var-1"))(parked.id, till).getOrThrow()

        assertTrue(list(till).getOrThrow().isEmpty())
    }

    @Test
    fun `a held cart can be thrown away`() = runTest {
        val parked = hold(basket(), "Ahmed", till).getOrThrow()

        discard(parked.id).getOrThrow()

        assertTrue(list(till).getOrThrow().isEmpty())
    }

    @Test
    fun `an unlabelled hold still gets a name`() = runTest {
        val parked = hold(basket(), "   ", till).getOrThrow()

        assertTrue(parked.label.isNotBlank())
    }
}
