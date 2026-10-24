package com.alsoug.keswa.features.inventory.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.DocumentStatus
import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.StockCount
import com.alsoug.keswa.core.domain.model.StockMovement
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustContent
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustUiState
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountContent
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountLineUiModel
import com.alsoug.keswa.features.inventory.presentation.screens.count.CountUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Visual regression proofs for Inventory: Adjustments and Blind Stock Counting.
 *
 * Renders into `features/inventory/build/ui-proofs/`.
 */
class InventoryProofs {

    private val sampleItem = SellableItem(
        variantId = "v1",
        productId = "p1",
        sku = "KSW-TSH-022-NV",
        name = "Round-neck t-shirt",
        nameAr = "تيشيرت رقبة دائرية",
        colourName = "Navy",
        colourNameAr = "كحلي",
        cost = Money.ofPounds(120),
        price = Money.ofPounds(180),
        onHand = 9,
    )

    private val sampleHistory = listOf(
        StockMovement("m1", "v1", "loc-shop", 24, MovementReason.RECEIPT, null, null, 0, "usr-1"),
        StockMovement("m2", "v1", "loc-shop", -12, MovementReason.SALE, "SALE", "s1", 1, "usr-1"),
        StockMovement(
            id = "m3",
            variantId = "v1",
            locationId = "loc-shop",
            quantity = -3,
            reason = MovementReason.DAMAGE,
            refType = null,
            refId = null,
            occurredAt = 2,
            userId = "usr-1",
            note = "water damage in the stockroom",
        ),
    )

    private val sampleCount = StockCount(
        id = "cnt-1",
        locationId = "loc-shop",
        status = DocumentStatus.DRAFT,
        note = null,
        startedAt = 0,
        startedByUserId = "usr-1",
    )

    private val sampleLines = listOf(
        CountLineUiModel("l1", "v1", "KSW-TSH-022-NV", "Round-neck t-shirt — Navy", counted = 9),
        CountLineUiModel("l2", "v2", "KSW-TSH-022-WH", "Round-neck t-shirt — White", counted = 14),
    )

    @Test
    fun `render all stock adjustment states`() {
        render("adjust-item") {
            Page(dark = false) {
                AdjustContent(
                    state = AdjustUiState(
                        item = sampleItem,
                        quantityEntry = "-3",
                        note = "water damage in the stockroom",
                        history = sampleHistory,
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        render("adjust-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                AdjustContent(
                    state = AdjustUiState(
                        item = sampleItem,
                        quantityEntry = "-3",
                        note = "تلف بالماء في المخزن",
                        history = sampleHistory,
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        render("adjust-dark") {
            Page(dark = true) {
                AdjustContent(
                    state = AdjustUiState(
                        item = sampleItem,
                        quantityEntry = "-3",
                        note = "water damage in the stockroom",
                        history = sampleHistory,
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun `render all stock counting states`() {
        render("count-open") {
            Page(dark = false) {
                CountContent(
                    state = CountUiState(
                        count = sampleCount,
                        lines = sampleLines,
                        note = "Annual inventory cycle",
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        render("count-posted") {
            Page(dark = false) {
                CountContent(
                    state = CountUiState(
                        count = sampleCount.copy(status = DocumentStatus.POSTED),
                        lines = listOf(
                            sampleLines[0].copy(expected = 12, variance = -3),
                            sampleLines[1].copy(expected = 14, variance = 0),
                        ),
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }

        render("count-dialog") {
            Page(dark = false) {
                CountContent(
                    state = CountUiState(
                        count = sampleCount,
                        lines = sampleLines,
                        pendingVariantId = "v3",
                        pendingSku = "KSW-TSH-022-BLK",
                        countedEntry = "15",
                    ),
                    onEvent = {},
                    onBack = {},
                )
            }
        }
    }
}

@Composable
private fun Page(
    dark: Boolean,
    language: KeswaLanguage = KeswaLanguage.ENGLISH,
    content: @Composable () -> Unit,
) {
    KeswaTheme(dark = dark, language = language) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

private val directory = File("build/ui-proofs").also { it.mkdirs() }

private fun render(name: String, width: Int = 1100, height: Int = 750, content: @Composable () -> Unit) {
    val density = 2f
    val scene = ImageComposeScene(
        width = (width * density).toInt(),
        height = (height * density).toInt(),
        density = Density(density),
        content = content,
    )
    try {
        val bytes = scene.render().encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: error("Skia declined to encode $name")
        directory.resolve("$name.png").writeBytes(bytes)
    } finally {
        scene.close()
    }
}
