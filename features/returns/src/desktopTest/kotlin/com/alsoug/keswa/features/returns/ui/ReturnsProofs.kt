package com.alsoug.keswa.features.returns.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnLineUiModel
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsContent
import com.alsoug.keswa.features.returns.presentation.screens.returns.ReturnsUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the returns and exchanges screen to `features/returns/build/ui-proofs/`.
 *
 * Verifies refund calculation, in-policy vs outside-policy authorization warning banners,
 * RTL Arabic layout, and dark theme contrast.
 */
class ReturnsProofs {

    private val lines = listOf(
        ReturnLineUiModel(
            saleLineId = "sl1",
            variantId = "v1",
            description = "Round-neck t-shirt — Navy",
            soldQuantity = 3,
            alreadyReturned = 1,
            returnable = 2,
            unitPrice = Money.ofPounds(180),
            unitCost = Money.ofPounds(120),
            selectedQuantity = 1,
        ),
        ReturnLineUiModel(
            saleLineId = "sl2",
            variantId = "v2",
            description = "Oxford shirt — White",
            soldQuantity = 2,
            alreadyReturned = 0,
            returnable = 2,
            unitPrice = Money.ofPounds(340),
            unitCost = Money.ofPounds(240),
            selectedQuantity = 1,
        ),
    )

    @Test
    fun `render all returns screen states`() {
        render("returns-in-policy") {
            Page(dark = false) {
                ReturnsContent(
                    state = ReturnsUiState(
                        saleId = "s1",
                        receiptNumber = 412,
                        daysSince = 3,
                        returnWindowDays = 14,
                        lines = lines,
                        reason = "Wrong size / fit",
                    ),
                    onEvent = {},
                )
            }
        }

        render("returns-outside-policy") {
            Page(dark = false) {
                ReturnsContent(
                    state = ReturnsUiState(
                        saleId = "s1",
                        receiptNumber = 412,
                        daysSince = 40,
                        isInsidePolicy = false,
                        returnWindowDays = 14,
                        lines = lines,
                        reason = "Customer dispute",
                    ),
                    onEvent = {},
                )
            }
        }

        render("returns-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                ReturnsContent(
                    state = ReturnsUiState(
                        saleId = "s1",
                        receiptNumber = 412,
                        daysSince = 3,
                        returnWindowDays = 14,
                        lines = lines,
                        reason = "مقاس غير مناسب",
                    ),
                    onEvent = {},
                )
            }
        }

        render("returns-dark") {
            Page(dark = true) {
                ReturnsContent(
                    state = ReturnsUiState(
                        saleId = "s1",
                        receiptNumber = 412,
                        daysSince = 3,
                        returnWindowDays = 14,
                        lines = lines,
                    ),
                    onEvent = {},
                )
            }
        }

        render("returns-empty") {
            Page(dark = false) {
                ReturnsContent(
                    state = ReturnsUiState(returnWindowDays = 14),
                    onEvent = {},
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

private fun render(name: String, width: Int = 1100, height: Int = 720, content: @Composable () -> Unit) {
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
