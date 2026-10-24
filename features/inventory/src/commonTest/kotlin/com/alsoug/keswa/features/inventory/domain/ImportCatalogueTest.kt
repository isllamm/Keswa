package com.alsoug.keswa.features.inventory.domain

import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.usecase.ImportResult
import com.alsoug.keswa.features.inventory.domain.usecase.ParseCatalogueImportUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A file with one bad row must not import 199 products and leave the operator guessing which one
 * failed — and every problem has to name its line, or it cannot be fixed.
 */
class ImportCatalogueTest {

    private val parse = ParseCatalogueImportUseCase()

    @Test
    fun `a clean file parses every row`() {
        val result = parse(
            """
            product,productAr,colour,sku,cost,price,quantity
            Round-neck t-shirt,تيشيرت,Navy,KSW-TSH-001-NV,120.00,180.00,20
            Round-neck t-shirt,تيشيرت,White,KSW-TSH-001-WH,118.50,180.00,30
            Oxford shirt,قميص,Blue,KSW-SHT-004-BL,240,380,5
            """.trimIndent(),
        )

        val rows = assertIs<ImportResult.Parsed>(result).rows
        assertEquals(3, rows.size)
        assertEquals(Money.ofPiastres(11_850), rows[1].cost)
        assertEquals(Money.ofPounds(380), rows[2].price)
        assertEquals(20, rows[0].quantity)
    }

    @Test
    fun `a file without a header still parses`() {
        val result = parse("Oxford shirt,قميص,Blue,KSW-SHT-004-BL,240,380,5")

        assertEquals(1, assertIs<ImportResult.Parsed>(result).rows.size)
    }

    @Test
    fun `quantity is optional, because the first import is usually a price list`() {
        val result = parse("Oxford shirt,قميص,Blue,KSW-SHT-004-BL,240,380")

        assertEquals(0, assertIs<ImportResult.Parsed>(result).rows.single().quantity)
    }

    @Test
    fun `one bad row rejects the whole file, naming its line`() {
        val result = parse(
            """
            product,productAr,colour,sku,cost,price,quantity
            Round-neck t-shirt,تيشيرت,Navy,KSW-TSH-001-NV,120.00,180.00,20
            Oxford shirt,قميص,Blue,KSW-SHT-004-BL,two hundred,380,5
            """.trimIndent(),
        )

        val problems = assertIs<ImportResult.Rejected>(result).problems
        assertEquals(1, problems.size)
        assertEquals(3, problems.single().lineNumber)
        assertTrue("not an amount" in problems.single().message)
    }

    @Test
    fun `every problem is reported, not just the first`() {
        val result = parse(
            """
            ,تيشيرت,Navy,KSW-TSH-001-NV,120,180,20
            Oxford shirt,قميص,,KSW-SHT-004-BL,240,380,5
            Linen shirt,كتان,Beige,,200,320,5
            """.trimIndent(),
        )

        val problems = assertIs<ImportResult.Rejected>(result).problems
        assertEquals(listOf(1, 2, 3), problems.map { it.lineNumber })
    }

    @Test
    fun `a repeated SKU is caught here, where it can be explained`() {
        val result = parse(
            """
            Round-neck t-shirt,تيشيرت,Navy,KSW-TSH-001-NV,120,180,20
            Oxford shirt,قميص,Blue,KSW-TSH-001-NV,240,380,5
            """.trimIndent(),
        )

        val problems = assertIs<ImportResult.Rejected>(result).problems
        // "row 2 repeats row 1's SKU" is fixable; "UNIQUE constraint failed" is not.
        assertEquals(2, problems.single().lineNumber)
        assertTrue("already appears on line 1" in problems.single().message)
    }

    @Test
    fun `a short row is rejected rather than guessed at`() {
        val result = parse("Oxford shirt,قميص,Blue")

        val problems = assertIs<ImportResult.Rejected>(result).problems
        assertTrue("expected 6 columns" in problems.single().message)
    }

    @Test
    fun `a negative quantity is refused`() {
        val result = parse("Oxford shirt,قميص,Blue,KSW-SHT-004-BL,240,380,-5")

        assertTrue("negative" in assertIs<ImportResult.Rejected>(result).problems.single().message)
    }

    @Test
    fun `an empty file says so rather than succeeding at nothing`() {
        assertIs<ImportResult.Rejected>(parse("   \n  \n"))
    }

    @Test
    fun `a missing Arabic name falls back to the Latin one`() {
        val result = parse("Oxford shirt,,Blue,KSW-SHT-004-BL,240,380,5")

        assertEquals("Oxford shirt", assertIs<ImportResult.Parsed>(result).rows.single().productNameAr)
    }

    @Test
    fun `blank lines are skipped, not counted as problems`() {
        val result = parse(
            """
            Oxford shirt,قميص,Blue,KSW-SHT-004-BL,240,380,5

            Linen shirt,كتان,Beige,KSW-SHT-009-BG,200,320,5
            """.trimIndent(),
        )

        assertEquals(2, assertIs<ImportResult.Parsed>(result).rows.size)
    }

    @Test
    fun `tab-separated text copied from Excel parses seamlessly`() {
        val tsv = "product\tproductAr\tcolour\tsku\tcost\tprice\tquantity\n" +
            "Denim Jacket\tجاكيت جينز\tIndigo\tKSW-JKT-001\t350\t550\t12"
        val result = parse(tsv)

        val rows = assertIs<ImportResult.Parsed>(result).rows
        assertEquals(1, rows.size)
        assertEquals("Denim Jacket", rows.single().productName)
        assertEquals(Money.ofPounds(350), rows.single().cost)
        assertEquals(12, rows.single().quantity)
    }

    @Test
    fun `quoted fields containing commas parse without splitting the cell`() {
        val csv = """
            product,productAr,colour,sku,cost,price,quantity
            "Shirt, Round Neck","قميص, رقبة دائرية",Navy,KSW-TSH-099,120,180,10
        """.trimIndent()
        val result = parse(csv)

        val rows = assertIs<ImportResult.Parsed>(result).rows
        assertEquals("Shirt, Round Neck", rows.single().productName)
        assertEquals("قميص, رقبة دائرية", rows.single().productNameAr)
    }

    @Test
    fun `Arabic header row is recognized and skipped`() {
        val csv = """
            المنتج,المنتج_عربي,اللون,الكود,التكلفة,السعر,الكمية
            Oxford shirt,قميص,Blue,KSW-SHT-004-BL,240,380,5
        """.trimIndent()
        val result = parse(csv)

        assertEquals(1, assertIs<ImportResult.Parsed>(result).rows.size)
    }

    @Test
    fun `currency prefixes in cost and price are gracefully handled`() {
        val csv = "Oxford shirt,قميص,Blue,KSW-SHT-004-BL,EGP 240,LE 380,5"
        val result = parse(csv)

        val row = assertIs<ImportResult.Parsed>(result).rows.single()
        assertEquals(Money.ofPounds(240), row.cost)
        assertEquals(Money.ofPounds(380), row.price)
    }
}
