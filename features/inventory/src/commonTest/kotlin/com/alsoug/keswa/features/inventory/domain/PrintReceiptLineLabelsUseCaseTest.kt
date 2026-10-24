package com.alsoug.keswa.features.inventory.domain

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.HangTagBuilder
import com.alsoug.keswa.features.inventory.domain.usecase.LabelRunResult
import com.alsoug.keswa.features.inventory.domain.usecase.PrintReceiptLineLabelsUseCase
import com.alsoug.keswa.features.inventory.fakes.FakeLabelNameRenderer
import com.alsoug.keswa.features.inventory.fakes.FakePriceRepository
import com.alsoug.keswa.features.inventory.fakes.FakePrinterTransport
import com.alsoug.keswa.features.inventory.fakes.FakeSellableRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSettingsRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockReceiptRepository
import com.alsoug.keswa.features.inventory.fakes.FakeTransportFactory
import com.alsoug.keswa.features.inventory.fakes.FakeVariantRepository
import com.alsoug.keswa.features.inventory.fakes.fullPermissionSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PrintReceiptLineLabelsUseCaseTest {

    private val sessions = fullPermissionSession()
    private val now = { 1_000_000L }
    private val prices = FakePriceRepository()
    private val receipts = FakeStockReceiptRepository()
    private val variants = FakeVariantRepository().withBarcode("v-1", "6221112223334")
    private val item = SellableItem(
        variantId = "v-1",
        productId = "p-1",
        sku = "OXF-NVY",
        name = "Oxford Shirt",
        nameAr = "قميص أكسفورد",
        colourName = "Navy",
        colourNameAr = "كحلي",
        cost = Money.ofPounds(150),
        price = Money.ofPounds(230),
        onHand = 10,
    )
    private val sellables = FakeSellableRepository().withItem(item, barcode = "6221112223334")
    private val find = FindStockItemUseCase(sellables, prices, now)
    private val ensureBarcode = EnsureVariantBarcodeUseCase(variants)
    private val nameRenderer = FakeLabelNameRenderer()
    private val tagBuilder = HangTagBuilder(nameRenderer)

    @Test
    fun `a draft line with a new price shows the new price and without one shows the live price`() = runTest {
        val settings = FakeSettingsRepository()
        val transport = FakePrinterTransport()
        val useCase = PrintReceiptLineLabelsUseCase(
            receipts = receipts,
            find = find,
            ensureBarcode = ensureBarcode,
            tags = tagBuilder,
            settings = settings,
            sessions = sessions,
            transports = FakeTransportFactory(transport),
        )

        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-shop", "usr-1", now())
        // Line with a new sellPrice (275.00 vs live 230.00)
        receipts.putLine(
            id = "line-1",
            receiptId = "rec-1",
            variantId = "v-1",
            quantity = 10,
            unitCost = Money.ofPounds(150),
            sellPrice = Money.ofPounds(275),
        )

        val pricedResult = useCase(receiptId = "rec-1", lineId = "line-1", copies = 4).getOrThrow()
        assertEquals(LabelRunResult.Printed(4), pricedResult)
        val pricedTspl = transport.sentPayloads.last().decodeToString()
        assertTrue(pricedTspl.contains("EGP 275.00"), pricedTspl)

        // Line without a sellPrice -> uses live price (230.00)
        receipts.putLine(
            id = "line-1",
            receiptId = "rec-1",
            variantId = "v-1",
            quantity = 10,
            unitCost = Money.ofPounds(150),
            sellPrice = null,
        )

        val liveResult = useCase(receiptId = "rec-1", lineId = "line-1", copies = 2).getOrThrow()
        assertEquals(LabelRunResult.Printed(2), liveResult)
        val liveTspl = transport.sentPayloads.last().decodeToString()
        assertTrue(liveTspl.contains("EGP 230.00"), liveTspl)
    }

    @Test
    fun `copies sent equals copies asked for`() = runTest {
        val settings = FakeSettingsRepository()
        val transport = FakePrinterTransport()
        val useCase = PrintReceiptLineLabelsUseCase(
            receipts = receipts,
            find = find,
            ensureBarcode = ensureBarcode,
            tags = tagBuilder,
            settings = settings,
            sessions = sessions,
            transports = FakeTransportFactory(transport),
        )

        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-shop", "usr-1", now())
        receipts.putLine(
            id = "line-1",
            receiptId = "rec-1",
            variantId = "v-1",
            quantity = 20,
            unitCost = Money.ofPounds(150),
            sellPrice = null,
        )

        val result = useCase(receiptId = "rec-1", lineId = "line-1", copies = 7).getOrThrow()
        assertEquals(LabelRunResult.Printed(7), result)
        val tspl = transport.sentPayloads.single().decodeToString()
        assertTrue(tspl.contains("PRINT 7,1"), tspl)
    }

    @Test
    fun `no printer returns NoPrinter and printer off returns Unreachable`() = runTest {
        receipts.createDraft("rec-1", "INV-1", "Supplier", "loc-shop", "usr-1", now())
        receipts.putLine(
            id = "line-1",
            receiptId = "rec-1",
            variantId = "v-1",
            quantity = 5,
            unitCost = Money.ofPounds(150),
            sellPrice = null,
        )

        val unconfiguredSettings = FakeSettingsRepository().apply {
            save(ShopSettings(labelHost = ""))
        }
        val noPrinterUseCase = PrintReceiptLineLabelsUseCase(
            receipts = receipts,
            find = find,
            ensureBarcode = ensureBarcode,
            tags = tagBuilder,
            settings = unconfiguredSettings,
            sessions = sessions,
            transports = FakeTransportFactory(),
        )
        assertEquals(
            LabelRunResult.NoPrinter,
            noPrinterUseCase("rec-1", "line-1", 5).getOrThrow(),
        )

        val deadTransport = FakePrinterTransport(failWith = IllegalStateException("connection refused"))
        val unreachableUseCase = PrintReceiptLineLabelsUseCase(
            receipts = receipts,
            find = find,
            ensureBarcode = ensureBarcode,
            tags = tagBuilder,
            settings = FakeSettingsRepository(),
            sessions = sessions,
            transports = FakeTransportFactory(deadTransport),
        )
        val result = unreachableUseCase("rec-1", "line-1", 5).getOrThrow()
        assertIs<LabelRunResult.Unreachable>(result)
        assertTrue(result.detail.contains("refused"))
    }
}
