package com.alsoug.keswa.features.sell.domain

import com.alsoug.keswa.core.domain.model.Payment
import com.alsoug.keswa.core.domain.model.Sale
import com.alsoug.keswa.core.domain.model.SaleLine
import com.alsoug.keswa.core.domain.model.SaleStatus
import com.alsoug.keswa.core.domain.model.ShopSettings
import com.alsoug.keswa.core.domain.model.TenderMethod
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.platform.TransportFactory
import com.alsoug.keswa.features.sell.domain.usecase.PrintReceiptUseCase
import com.alsoug.keswa.features.sell.domain.usecase.ReceiptResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Printing sits outside the commit, so the failures here are all recoverable ones.
 *
 * A sale that committed but did not print gets reprinted. The opposite arrangement leaves a
 * customer holding a receipt for a transaction the shop has no record of.
 */
class PrintReceiptUseCaseTest {

    private val renderer = RecordingRenderer()

    private fun sale(
        tender: TenderMethod = TenderMethod.CASH,
        discount: Money = Money.ofPounds(40),
    ): Sale {
        val total = Money.ofPounds(320)
        return Sale(
            id = "sale-1",
            receiptNumber = 17,
            locationId = "loc-shop",
            priceListId = "pricelist-retail",
            userId = "usr-seller",
            shiftId = "shift-1",
            status = SaleStatus.COMPLETED,
            subtotal = Money.ofPounds(360),
            discount = discount,
            tax = Money.ZERO,
            total = total,
            tendered = total,
            change = Money.ZERO,
            occurredAt = 1_757_000_000_000,
            lines = listOf(
                SaleLine(
                    id = "line-1",
                    saleId = "sale-1",
                    lineNumber = 1,
                    variantId = "var-1",
                    description = "Round-neck t-shirt — Navy",
                    descriptionAr = "تيشيرت — كحلي",
                    quantity = 2,
                    unitPrice = Money.ofPounds(180),
                    lineDiscount = discount,
                    orderDiscount = Money.ZERO,
                    lineTotal = total,
                    tax = Money.ZERO,
                    unitCost = Money.ofPounds(120),
                ),
            ),
            payments = listOf(
                Payment("pay-1", "sale-1", tender, total, total, null, 1_757_000_000_000),
            ),
        )
    }

    private fun useCase(
        settings: ShopSettings,
        transport: RecordingTransport = RecordingTransport(),
    ): Pair<PrintReceiptUseCase, RecordingTransport> {
        val factory = TransportFactory { _, _ -> transport }
        return PrintReceiptUseCase(
            settings = FakeSettingsRepository(settings),
            sales = RecordingSaleRepository(),
            renderer = renderer,
            transports = factory,
        ) to transport
    }

    private val configured = ShopSettings(
        shopName = "Keswa",
        shopNameAr = "كسوة",
        addressLine = "12 Talaat Harb",
        receiptHost = "192.168.1.50",
    )

    @Test
    fun `a receipt carries the shop, the lines and the sale's id`() = runTest {
        val (print, _) = useCase(configured)

        assertIs<ReceiptResult.Printed>(print(sale()).getOrThrow())

        val receipt = assertNotNull(renderer.lastReceipt)
        assertEquals("Keswa", receipt.shopName)
        assertEquals("12 Talaat Harb", receipt.addressLine)
        assertEquals(1, receipt.lines.size)
        assertEquals(2, receipt.itemCount)
        // The QR carries the id, not the number: Phase 8 scans it to find a sale to return
        // against, and the id is what survives a merge between two tills.
        assertEquals("sale-1", receipt.saleId)
        assertTrue(receipt.hasDiscount)
    }

    @Test
    fun `a cash sale pops the drawer and a card sale does not`() = runTest {
        val (printCash, cashTransport) = useCase(configured)
        printCash(sale(TenderMethod.CASH)).getOrThrow()
        val cashBytes = assertNotNull(cashTransport.sent)

        val (printCard, cardTransport) = useCase(configured)
        printCard(sale(TenderMethod.CARD)).getOrThrow()
        val cardBytes = assertNotNull(cardTransport.sent)

        // ESC p 0 — the kick. An unexplained open drawer is what a shift count exists to catch,
        // so it must not fire on a card sale.
        val kick = byteArrayOf(0x1B, 0x70, 0x00)
        assertTrue(cashBytes.containsSequence(kick), "a cash sale should open the drawer")
        assertTrue(!cardBytes.containsSequence(kick), "a card sale should leave the drawer shut")
    }

    @Test
    fun `no printer configured is a plain answer, not a failure`() = runTest {
        val (print, transport) = useCase(ShopSettings())

        assertIs<ReceiptResult.NoPrinter>(print(sale()).getOrThrow())
        assertEquals(0, transport.openCount)
    }

    @Test
    fun `an unreachable printer is reported, and the call still succeeds`() = runTest {
        val (print, _) = useCase(configured, RecordingTransport(failOnOpen = true))

        val result = print(sale()).getOrThrow()

        // A returned value, not an exception: "the printer is off" is an ordinary outcome, and the
        // sale it belongs to has already committed.
        val unreachable = assertIs<ReceiptResult.Unreachable>(result)
        assertTrue(unreachable.detail.isNotBlank())
    }
}

private fun ByteArray.containsSequence(needle: ByteArray): Boolean =
    indices.any { start ->
        start + needle.size <= size && needle.indices.all { this[start + it] == needle[it] }
    }
