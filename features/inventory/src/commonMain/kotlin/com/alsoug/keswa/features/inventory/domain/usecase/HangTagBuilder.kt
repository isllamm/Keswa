package com.alsoug.keswa.features.inventory.domain.usecase

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.platform.ILabelNameRenderer
import com.alsoug.keswa.core.printing.tspl.LabelSpec
import com.alsoug.keswa.core.printing.tspl.Tspl

/** The TSPL for one colour's tags. */
class HangTagBuilder(private val names: ILabelNameRenderer) {

    fun build(
        spec: LabelSpec,
        item: SellableItem,
        barcode: String,
        price: Money,
        copies: Int,
    ): ByteArray =
        Tspl.label(
            spec = spec,
            barcode = barcode,
            price = "EGP ${price.format()}",
            sku = item.sku,
            nameBitmap = names.render(tagName(item), spec.widthDots - 20, NAME_HEIGHT_DOTS),
            copies = copies,
        )

    /** Arabic when the product has an Arabic name; English otherwise, so a tag is never blank. */
    private fun tagName(item: SellableItem): String =
        if (item.nameAr.isNotBlank()) {
            "${item.nameAr} — ${item.colourNameAr.ifBlank { item.colourName }}"
        } else {
            item.description
        }

    private companion object {
        const val NAME_HEIGHT_DOTS = 40
    }
}
