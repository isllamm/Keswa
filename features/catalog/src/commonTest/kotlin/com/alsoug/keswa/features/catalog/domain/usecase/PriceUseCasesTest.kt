package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.InMemorySessionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

/**
 * The gap Phase 5 found: the price tables shipped in v1, and until now nothing wrote to them — so
 * the till had nothing to ring up.
 */
class PriceUseCasesTest {

    private val prices = FakePriceRepository()

    private fun sellerSession(): ISessionManager = InMemorySessionManager().apply {
        signIn(User("usr-seller", "sara", "Sara", "سارة", UserRole.SELLER), atMillis = 0)
    }

    private fun setPrice(sessions: ISessionManager = adminSession()) =
        SetRetailPriceUseCase(prices, sessions, SequentialIds("price")) { 1_757_000_000_000 }

    private val getPrice = GetRetailPriceUseCase(prices) { 1_757_000_000_000 }

    @Test
    fun `an admin can price a variant`() = runTest {
        assertIs<SetPriceResult.Saved>(setPrice()("var-1", "180.50").getOrThrow())

        assertEquals(Money.ofPiastres(18_050), getPrice("var-1").getOrThrow())
    }

    @Test
    fun `a seller cannot`() = runTest {
        // Checked in the domain, so hiding the field would not have been the control.
        assertIs<Error.ForbiddenAccess>(setPrice(sellerSession())("var-1", "180").exceptionOrNull())

        assertNull(getPrice("var-1").getOrThrow())
    }

    @Test
    fun `an unpriced variant reads as no price, not as zero`() = runTest {
        // The till refuses an unpriced SKU rather than ringing up nothing.
        assertNull(getPrice("var-unknown").getOrThrow())
    }

    @Test
    fun `something that is not an amount is refused rather than rounded`() = runTest {
        assertIs<SetPriceResult.NotAnAmount>(setPrice()("var-1", "one eighty").getOrThrow())
        assertIs<SetPriceResult.NotAnAmount>(setPrice()("var-1", "180.505").getOrThrow())
        assertIs<SetPriceResult.NotAnAmount>(setPrice()("var-1", "-5").getOrThrow())

        assertNull(getPrice("var-1").getOrThrow())
    }

    @Test
    fun `a new price replaces the one in force`() = runTest {
        setPrice()("var-1", "180").getOrThrow()
        setPrice()("var-1", "150").getOrThrow()

        assertEquals(Money.ofPounds(150), getPrice("var-1").getOrThrow())
    }
}
