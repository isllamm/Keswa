package com.alsoug.keswa.features.wholesale.domain

import com.alsoug.keswa.core.domain.model.AssortmentPack
import com.alsoug.keswa.core.domain.model.AssortmentPackLine
import com.alsoug.keswa.core.domain.model.Permission
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.User
import com.alsoug.keswa.core.domain.model.UserRole
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IAssortmentPackRepository
import com.alsoug.keswa.core.domain.repository.ISellableRepository
import com.alsoug.keswa.core.error.Error
import com.alsoug.keswa.core.session.ISessionManager
import com.alsoug.keswa.core.session.InMemorySessionManager
import com.alsoug.keswa.features.wholesale.domain.usecase.ExpandAssortmentPackUseCase
import com.alsoug.keswa.features.wholesale.domain.usecase.PackExpansion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * "A carton: 20 navy, 30 white, 10 beige, one price."
 *
 * The check that costs money is the last one: a carton whose lines add up to a pound less than
 * what was quoted is the wholesale version of the classic POS bug, and it is invisible until a
 * customer adds up their own invoice.
 */
class PackAllocationTest {

    private class FakePacks(private val pack: AssortmentPack?) : IAssortmentPackRepository {
        override suspend fun create(
            id: String,
            name: String,
            nameAr: String,
            price: Money,
        ): Result<AssortmentPack> = error("not used")

        override suspend fun addLine(
            id: String,
            packId: String,
            variantId: String,
            quantity: Int,
        ): Result<AssortmentPack> = error("not used")

        override suspend fun removeLine(packId: String, lineId: String): Result<AssortmentPack> =
            error("not used")

        override suspend fun getById(id: String): Result<AssortmentPack?> =
            Result.success(pack?.takeIf { it.id == id })

        override suspend fun getAll(): Result<List<AssortmentPack>> =
            Result.success(listOfNotNull(pack))
    }

    private class FakeSellables(private val items: List<SellableItem>) : ISellableRepository {
        override suspend fun byBarcode(
            barcode: String,
            priceListId: String,
            locationId: String,
            at: Long,
        ): Result<SellableItem?> = Result.success(null)

        override suspend fun byVariantId(
            variantId: String,
            priceListId: String,
            locationId: String,
            at: Long,
        ): Result<SellableItem?> = Result.success(items.firstOrNull { it.variantId == variantId })

        override suspend fun search(
            term: String,
            priceListId: String,
            locationId: String,
            at: Long,
            limit: Int,
        ): Result<List<SellableItem>> = Result.success(emptyList())
    }

    private fun item(id: String, price: Long?) = SellableItem(
        variantId = id,
        productId = "prod-1",
        sku = "SKU-$id",
        name = "T-shirt",
        nameAr = "تيشيرت",
        colourName = id,
        colourNameAr = id,
        cost = Money.ofPounds(80),
        price = price?.let { Money.ofPounds(it) },
        onHand = 100,
    )

    private val pack = AssortmentPack(
        id = "pack-1",
        name = "Mixed carton",
        nameAr = "كرتونة مشكلة",
        price = Money.ofPounds(9_000),
        isActive = true,
        lines = listOf(
            AssortmentPackLine("l1", "pack-1", "navy", 20),
            AssortmentPackLine("l2", "pack-1", "white", 30),
            AssortmentPackLine("l3", "pack-1", "beige", 10),
        ),
    )

    private fun session(role: UserRole?): ISessionManager = InMemorySessionManager().apply {
        role?.let { signIn(User("usr-1", "sara", "Sara", "سارة", it), atMillis = 0) }
    }

    private fun expand(
        items: List<SellableItem> = listOf(item("navy", 180), item("white", 150), item("beige", 120)),
        packOverride: AssortmentPack? = pack,
        role: UserRole? = UserRole.SELLER,
    ) = ExpandAssortmentPackUseCase(
        packs = FakePacks(packOverride),
        sellables = FakeSellables(items),
        sessions = session(role),
        now = { 0 },
    )

    @Test
    fun `a carton becomes one line per variant`() = runTest {
        val expanded = assertIs<PackExpansion.Expanded>(
            expand()("pack-1", "pricelist-trade", "loc-shop").getOrThrow(),
        )

        assertEquals(3, expanded.lines.size)
        assertEquals(listOf(20, 30, 10), expanded.lines.map { it.quantity })
        // Each line remembers the carton, so a receipt can group it back together.
        assertTrue(expanded.lines.all { it.packId == "pack-1" })
    }

    @Test
    fun `the lines sum to exactly the carton price`() = runTest {
        val expanded = assertIs<PackExpansion.Expanded>(
            expand()("pack-1", "pricelist-trade", "loc-shop").getOrThrow(),
        )

        // Largest-remainder, so the piastres rounding would lose go to a line rather than vanish.
        val total = expanded.lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal }
        assertEquals(Money.ofPounds(9_000), total)
    }

    @Test
    fun `an awkward price still sums exactly`() = runTest {
        val awkward = pack.copy(price = Money.ofPiastres(100_003))

        val expanded = assertIs<PackExpansion.Expanded>(
            expand(packOverride = awkward)("pack-1", "pricelist-trade", "loc-shop").getOrThrow(),
        )

        assertEquals(
            Money.ofPiastres(100_003),
            expanded.lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal },
        )
    }

    @Test
    fun `an expensive garment carries more of the carton than a cheap one`() = runTest {
        val expanded = assertIs<PackExpansion.Expanded>(
            expand()("pack-1", "pricelist-trade", "loc-shop").getOrThrow(),
        )

        // Values are 20×180, 30×150 and 10×120 — 3,600 / 4,500 / 1,200 out of 9,300.
        val navy = expanded.lines.single { it.item.variantId == "navy" }
        val white = expanded.lines.single { it.item.variantId == "white" }
        val beige = expanded.lines.single { it.item.variantId == "beige" }

        assertTrue(white.lineTotal > navy.lineTotal)
        assertTrue(navy.lineTotal > beige.lineTotal)
    }

    @Test
    fun `an unpriced pack falls back to quantity`() = runTest {
        val unpriced = listOf(item("navy", null), item("white", null), item("beige", null))

        val expanded = assertIs<PackExpansion.Expanded>(
            expand(items = unpriced)("pack-1", "pricelist-trade", "loc-shop").getOrThrow(),
        )

        // Weighted 20/30/10: a pack of stock nobody has priced still has to be sellable.
        assertEquals(Money.ofPounds(3_000), expanded.lines.single { it.item.variantId == "navy" }.lineTotal)
        assertEquals(Money.ofPounds(4_500), expanded.lines.single { it.item.variantId == "white" }.lineTotal)
        assertEquals(Money.ofPounds(1_500), expanded.lines.single { it.item.variantId == "beige" }.lineTotal)
    }

    @Test
    fun `several cartons multiply quantity and price together`() = runTest {
        val expanded = assertIs<PackExpansion.Expanded>(
            expand()("pack-1", "pricelist-trade", "loc-shop", cartons = 3).getOrThrow(),
        )

        assertEquals(listOf(60, 90, 30), expanded.lines.map { it.quantity })
        assertEquals(
            Money.ofPounds(27_000),
            expanded.lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal },
        )
    }

    @Test
    fun `a retired variant is reported rather than silently dropped`() = runTest {
        val partial = listOf(item("navy", 180), item("white", 150))

        val result = expand(items = partial)("pack-1", "pricelist-trade", "loc-shop").getOrThrow()

        val incomplete = assertIs<PackExpansion.Incomplete>(result)
        assertEquals(listOf("beige"), incomplete.missing)
        assertEquals(2, incomplete.lines.size)
        // Still exact across what remains — the carton is short, not mispriced.
        assertEquals(
            Money.ofPounds(9_000),
            incomplete.lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal },
        )
    }

    @Test
    fun `a pack that does not exist says so`() = runTest {
        assertIs<PackExpansion.NotFound>(
            expand(packOverride = null)("pack-1", "pricelist-trade", "loc-shop").getOrThrow(),
        )
    }

    @Test
    fun `an empty pack says so`() = runTest {
        val empty = pack.copy(lines = emptyList())

        assertIs<PackExpansion.Empty>(
            expand(packOverride = empty)("pack-1", "pricelist-trade", "loc-shop").getOrThrow(),
        )
    }

    @Test
    fun `nobody expands a carton without a session`() = runTest {
        val thrown = expand(role = null)("pack-1", "pricelist-trade", "loc-shop").exceptionOrNull()

        assertIs<Error.ForbiddenAccess>(thrown)
        assertEquals(Permission.SELL.name, "SELL")
    }
}
