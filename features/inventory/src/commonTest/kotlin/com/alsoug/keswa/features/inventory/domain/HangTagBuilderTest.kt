package com.alsoug.keswa.features.inventory.domain

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.features.inventory.domain.usecase.HangTagBuilder
import com.alsoug.keswa.features.inventory.fakes.FakeLabelNameRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HangTagBuilderTest {

    private val renderer = FakeLabelNameRenderer()
    private val builder = HangTagBuilder(renderer)
    private val spec = LabelSpec()

    @Test
    fun `a product with an Arabic name renders the Arabic product and colour name`() {
        val item = SellableItem(
            variantId = "v-1",
            productId = "p-1",
            sku = "OXF-NVY",
            name = "Oxford Shirt",
            nameAr = "قميص أكسفورد",
            colourName = "Navy",
            colourNameAr = "كحلي",
            cost = Money.ofPounds(150),
            price = Money.ofPounds(290),
            onHand = 10,
        )

        val bytes = builder.build(
            spec = spec,
            item = item,
            barcode = "2000000000015",
            price = Money.ofPounds(290),
            copies = 3,
        )

        assertEquals(listOf("قميص أكسفورد — كحلي"), renderer.renderedTexts)
        val tspl = bytes.decodeToString()
        assertTrue(tspl.contains("BITMAP 10,10,38,8,0,"), tspl)
        assertTrue(tspl.contains("EGP 290.00"), tspl)
        assertTrue(tspl.contains("PRINT 3,1"), tspl)
    }

    @Test
    fun `a product with no Arabic name gets the English name`() {
        val item = SellableItem(
            variantId = "v-2",
            productId = "p-2",
            sku = "LIN-WHT",
            name = "Linen Trousers",
            nameAr = "",
            colourName = "White",
            colourNameAr = "أبيض",
            cost = Money.ofPounds(200),
            price = Money.ofPounds(380),
            onHand = 5,
        )

        builder.build(
            spec = spec,
            item = item,
            barcode = "2000000000022",
            price = Money.ofPounds(380),
            copies = 1,
        )

        assertEquals(listOf("Linen Trousers — White"), renderer.renderedTexts)
    }
}
