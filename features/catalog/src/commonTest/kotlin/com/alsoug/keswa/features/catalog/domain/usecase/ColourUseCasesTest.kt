package com.alsoug.keswa.features.catalog.domain.usecase

import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.features.catalog.domain.Ean13
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AddColourToProductUseCaseTest {

    private val products = FakeProductRepository().given(product())
    private val colours = FakeColourRepository().given(colour())
    private val variants = FakeVariantRepository()

    private val useCase = AddColourToProductUseCase(
        products = products,
        colours = colours,
        variants = variants,
        generateBarcode = GenerateInternalBarcodeUseCase(variants),
        ids = SequentialIds("var"),
    )

    @Test
    fun `adding a colour creates a SKU and prints it a barcode`() = runTest {
        // When navy is added to the Oxford shirt
        val result = useCase("p1", "c1").getOrThrow()

        // Then a readable SKU and a scannable in-store code come back together
        val added = assertIs<AddColourResult.Added>(result)
        assertEquals("OXF-NAV", added.variant.sku)
        assertTrue(Ean13.isInStore(added.barcode), "${added.barcode} should be an in-store EAN-13")

        val attached = variants.barcodesFor(added.variant.id).getOrThrow()
        assertEquals(1, attached.size)
        assertEquals(BarcodeSource.OWN, attached.single().source)
        assertTrue(attached.single().isPrimary)
    }

    @Test
    fun `the same colour cannot be stocked twice`() = runTest {
        // Given navy is already stocked
        useCase("p1", "c1").getOrThrow()

        // When it is added again
        val result = useCase("p1", "c1").getOrThrow()

        // Then it is refused — two "navy" rows would split the same SKU's stock
        assertIs<AddColourResult.AlreadyStocked>(result)
        assertEquals(1, variants.forProduct("p1").getOrThrow().size)
    }

    @Test
    fun `colliding SKUs are disambiguated rather than rejected`() = runTest {
        // Given a second product whose name yields the same SKU stem
        products.given(product(id = "p2", name = "Oxford shirt slim"))
        colours.given(colour(id = "c1"))
        useCase("p1", "c1").getOrThrow()

        // When the second product takes the same colour
        val result = useCase("p2", "c1").getOrThrow()

        // Then it gets a distinct SKU instead of failing
        val added = assertIs<AddColourResult.Added>(result)
        assertEquals("OXF-NAV-2", added.variant.sku)
    }

    @Test
    fun `each new colour gets its own barcode`() = runTest {
        colours.given(colour(id = "c2", name = "Red"))

        val first = assertIs<AddColourResult.Added>(useCase("p1", "c1").getOrThrow())
        val second = assertIs<AddColourResult.Added>(useCase("p1", "c2").getOrThrow())

        assertTrue(first.barcode != second.barcode)
        assertTrue(Ean13.isValid(second.barcode))
    }

    @Test
    fun `missing product or colour is reported, not thrown`() = runTest {
        assertIs<AddColourResult.ProductNotFound>(useCase("nope", "c1").getOrThrow())
        assertIs<AddColourResult.ColourNotFound>(useCase("p1", "nope").getOrThrow())
    }
}

class RemoveColourFromProductUseCaseTest {

    private val variants = FakeVariantRepository()
    private val useCase = RemoveColourFromProductUseCase(variants)

    @Test
    fun `a colour with stock on the rail cannot be removed`() = runTest {
        // Given a variant with 7 pieces on hand
        variants.addColour("v1", "p1", "c1", "OXF-NAV", com.alsoug.keswa.core.domain.money.Money.ZERO)
        variants.givenStock("v1", 7)

        // When removal is attempted
        val result = useCase("v1").getOrThrow()

        // Then it is refused and says how many are in the way
        assertEquals(RemoveColourResult.HasStock(7), result)
        assertTrue(variants.getById("v1").getOrThrow()!!.isActive)
    }

    @Test
    fun `an empty colour is deactivated, never deleted`() = runTest {
        // Given a variant that has sold out
        variants.addColour("v1", "p1", "c1", "OXF-NAV", com.alsoug.keswa.core.domain.money.Money.ZERO)
        variants.givenStock("v1", 0)

        val result = useCase("v1").getOrThrow()

        // Then the row survives so the ledger still resolves — it is only retired
        assertEquals(RemoveColourResult.Removed, result)
        val variant = variants.getById("v1").getOrThrow()
        assertTrue(variant != null, "the variant row must outlive the decision to stop selling it")
        assertTrue(!variant!!.isActive)
    }

    @Test
    fun `negative stock still blocks removal`() = runTest {
        // An oversold variant is a discrepancy to investigate, not a colour to quietly retire.
        variants.addColour("v1", "p1", "c1", "OXF-NAV", com.alsoug.keswa.core.domain.money.Money.ZERO)
        variants.givenStock("v1", -3)

        assertEquals(RemoveColourResult.HasStock(-3), useCase("v1").getOrThrow())
    }
}

class AssignSupplierBarcodeUseCaseTest {

    private val variants = FakeVariantRepository()
    private val useCase = AssignSupplierBarcodeUseCase(variants)

    private suspend fun seed() =
        variants.addColour("v1", "p1", "c1", "OXF-NAV", com.alsoug.keswa.core.domain.money.Money.ZERO)

    @Test
    fun `a scanned supplier code is attached alongside our own`() = runTest {
        seed()
        variants.attachBarcode("2000000000015", "v1", BarcodeSource.OWN, isPrimary = true)

        val result = useCase("v1", "5901234123457").getOrThrow()

        // Then both codes resolve to the same SKU — the reason barcodes are their own table
        assertIs<AssignBarcodeResult.Assigned>(result)
        assertEquals(2, variants.barcodesFor("v1").getOrThrow().size)
        assertEquals("v1", variants.findByBarcode("5901234123457").getOrThrow()?.id)
        assertEquals("v1", variants.findByBarcode("2000000000015").getOrThrow()?.id)
    }

    @Test
    fun `a mistyped barcode is caught by its check digit`() = runTest {
        seed()
        assertIs<AssignBarcodeResult.NotAnEan13>(useCase("v1", "5901234123458").getOrThrow())
        assertTrue(variants.barcodesFor("v1").getOrThrow().isEmpty())
    }

    @Test
    fun `a barcode already in use is refused`() = runTest {
        seed()
        variants.addColour("v2", "p1", "c2", "OXF-RED", com.alsoug.keswa.core.domain.money.Money.ZERO)
        useCase("v1", "5901234123457").getOrThrow()

        // When another variant claims the same code
        val result = useCase("v2", "5901234123457").getOrThrow()

        // Then it is refused — a scan must never be ambiguous
        assertIs<AssignBarcodeResult.AlreadyInUse>(result)
    }

    @Test
    fun `assigning to a variant that does not exist is reported`() = runTest {
        assertIs<AssignBarcodeResult.VariantNotFound>(useCase("ghost", "5901234123457").getOrThrow())
    }
}
