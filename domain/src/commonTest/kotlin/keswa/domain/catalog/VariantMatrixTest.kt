package keswa.domain.catalog

import keswa.core.common.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun value(ar: String) = OptionValueInput(ar)

class VariantMatrixTest {

    @Test
    fun `no options produces exactly one variant with no name suffix`() {
        val result = VariantMatrix.generate(emptyList())
        assertIs<AppResult.Ok<List<GeneratedVariant>>>(result)
        assertEquals(1, result.value.size)
        assertEquals("", result.value.single().nameSuffix)
    }

    @Test
    fun `options with empty value lists are ignored, same as no options`() {
        val result = VariantMatrix.generate(listOf(OptionInput("Size", values = emptyList())))
        assertIs<AppResult.Ok<List<GeneratedVariant>>>(result)
        assertEquals(1, result.value.size)
    }

    @Test
    fun `a single option produces one variant per value, in order`() {
        val sizes = OptionInput("Size", values = listOf(value("S"), value("M"), value("L")))
        val result = VariantMatrix.generate(listOf(sizes))
        assertIs<AppResult.Ok<List<GeneratedVariant>>>(result)
        assertEquals(listOf("S", "M", "L"), result.value.map { it.nameSuffix })
    }

    @Test
    fun `two options produce the full cross product`() {
        val sizes = OptionInput("Size", values = listOf(value("S"), value("M")))
        val colors = OptionInput("Colour", values = listOf(value("Red"), value("Blue"), value("Green")))
        val result = VariantMatrix.generate(listOf(sizes, colors))
        assertIs<AppResult.Ok<List<GeneratedVariant>>>(result)
        assertEquals(6, result.value.size)
        assertEquals(
            setOf("S / Red", "S / Blue", "S / Green", "M / Red", "M / Blue", "M / Green"),
            result.value.map { it.nameSuffix }.toSet(),
        )
    }

    @Test
    fun `three options compound correctly`() {
        val a = OptionInput("A", values = listOf(value("1"), value("2")))
        val b = OptionInput("B", values = listOf(value("x"), value("y")))
        val c = OptionInput("C", values = listOf(value("p"), value("q")))
        val result = VariantMatrix.generate(listOf(a, b, c))
        assertIs<AppResult.Ok<List<GeneratedVariant>>>(result)
        assertEquals(8, result.value.size)
        assertEquals(8, result.value.map { it.nameSuffix }.toSet().size, "all combinations must be distinct")
    }

    @Test
    fun `exceeding the variant cap is rejected`() {
        val bigOption = OptionInput("X", values = (1..15).map { value(it.toString()) })
        val result = VariantMatrix.generate(listOf(bigOption, bigOption, bigOption)) // 15^3 = 3375
        assertIs<AppResult.Err>(result)
        assertIs<CatalogError.TooManyVariants>(result.error)
    }

    @Test
    fun `exactly at the cap is allowed`() {
        val option = OptionInput("X", values = (1..MviCapSize).map { value(it.toString()) })
        val result = VariantMatrix.generate(listOf(option))
        assertIs<AppResult.Ok<List<GeneratedVariant>>>(result)
        assertEquals(VariantMatrix.MAX_VARIANTS, result.value.size)
    }

    private val MviCapSize get() = VariantMatrix.MAX_VARIANTS

    @Test
    fun `option values keep their english name for sku-building purposes`() {
        val sizes = OptionInput("Size", values = listOf(OptionValueInput("صغير", "S")))
        val result = VariantMatrix.generate(listOf(sizes))
        assertIs<AppResult.Ok<List<GeneratedVariant>>>(result)
        assertEquals("S", result.value.single().optionValues.single().valueEn)
        assertTrue(result.value.single().nameSuffix.contains("صغير"))
    }
}
