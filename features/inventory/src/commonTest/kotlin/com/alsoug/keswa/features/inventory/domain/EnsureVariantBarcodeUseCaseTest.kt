package com.alsoug.keswa.features.inventory.domain

import com.alsoug.keswa.core.domain.model.Barcode
import com.alsoug.keswa.core.domain.model.BarcodeSource
import com.alsoug.keswa.core.domain.model.Variant
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class EnsureVariantBarcodeUseCaseTest {

    private val fakeVariantRepository = FakeTestVariantRepository()
    private val ensureBarcodeUseCase = EnsureVariantBarcodeUseCase(fakeVariantRepository)

    @Test
    fun `returns existing primary barcode if variant already has one`() = runTest {
        val variantId = "var-101"
        fakeVariantRepository.attachBarcode("6220000000018", variantId, BarcodeSource.OWN, isPrimary = true)

        val result = ensureBarcodeUseCase(variantId)

        assertTrue(result.isSuccess)
        assertEquals("6220000000018", result.getOrThrow())
        assertEquals(1, fakeVariantRepository.barcodes.size)
    }

    @Test
    fun `auto generates EAN-13 barcode when variant lacks a barcode`() = runTest {
        val variantId = "var-202"

        val result = ensureBarcodeUseCase(variantId)

        assertTrue(result.isSuccess)
        val generated = result.getOrThrow()
        assertTrue(generated.startsWith("200"))
        assertEquals(13, generated.length)

        val attached = fakeVariantRepository.barcodesFor(variantId).getOrThrow().single()
        assertEquals(generated, attached.barcode)
        assertTrue(attached.isPrimary)
        assertEquals(BarcodeSource.OWN, attached.source)
    }
}

private class FakeTestVariantRepository : IVariantRepository {
    val barcodes = mutableListOf<Barcode>()
    val variants = mutableMapOf<String, Variant>()

    override suspend fun addColour(
        id: String,
        productId: String,
        colourId: String,
        sku: String,
        cost: Money,
    ): Result<Variant> {
        val v = Variant(id, productId, colourId, sku, cost, true)
        variants[id] = v
        return Result.success(v)
    }

    override suspend fun forProduct(productId: String): Result<List<Variant>> =
        Result.success(variants.values.filter { it.productId == productId })

    override suspend fun skuExists(sku: String): Result<Boolean> =
        Result.success(variants.values.any { it.sku == sku })

    override suspend fun getById(id: String): Result<Variant?> = Result.success(variants[id])

    override suspend fun deactivate(id: String): Result<Unit> = Result.success(Unit)

    override suspend fun onHand(variantId: String): Result<Int> = Result.success(0)

    override suspend fun attachBarcode(
        barcode: String,
        variantId: String,
        source: BarcodeSource,
        isPrimary: Boolean,
    ): Result<Unit> {
        barcodes += Barcode(barcode, variantId, isPrimary, source)
        return Result.success(Unit)
    }

    override suspend fun barcodesFor(variantId: String): Result<List<Barcode>> =
        Result.success(barcodes.filter { it.variantId == variantId })

    override suspend fun findByBarcode(barcode: String): Result<Variant?> =
        Result.success(barcodes.firstOrNull { it.barcode == barcode }?.let { variants[it.variantId] })

    override suspend fun ownBarcodeCount(): Result<Int> =
        Result.success(barcodes.count { it.source == BarcodeSource.OWN })
}
