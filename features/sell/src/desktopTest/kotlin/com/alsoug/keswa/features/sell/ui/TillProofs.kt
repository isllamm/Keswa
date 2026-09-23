package com.alsoug.keswa.features.sell.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.features.sell.domain.model.Basket
import com.alsoug.keswa.features.sell.domain.model.BasketLine
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.sell.domain.usecase.CalculateBasketTotalUseCase
import com.alsoug.keswa.features.sell.presentation.screens.till.TillContent
import com.alsoug.keswa.features.sell.presentation.screens.till.TillUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders the till to `features/sell/build/ui-proofs/`.
 *
 * It lives here rather than beside the other proofs in `:composeApp` because `TillContent` is
 * `internal` — and it stays that way. A screen's stateless half being reachable only from its own
 * module is the point of ADR-012; widening it so a proof can see it would be the tail wagging the
 * dog.
 */
class TillProofs {

    @Test
    fun `the till, busy and empty`() {
        render("till-busy") {
            Page(dark = false) {
                TillContent(
                    state = TillUiState(
                        basket = proofBasket,
                        totals = CalculateBasketTotalUseCase()(proofBasket, vatBasisPoints = 1_400),
                        vatBasisPoints = 1_400,
                        lastSaleId = "sale-1",
                        lastReceiptNumber = 412,
                    ),
                    onEvent = {},
                )
            }
        }

        render("till-busy-dark") {
            Page(dark = true) {
                TillContent(
                    state = TillUiState(
                        basket = proofBasket,
                        totals = CalculateBasketTotalUseCase()(proofBasket, vatBasisPoints = 1_400),
                        vatBasisPoints = 1_400,
                    ),
                    onEvent = {},
                )
            }
        }

        render("till-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                TillContent(
                    state = TillUiState(
                        basket = proofBasket,
                        totals = CalculateBasketTotalUseCase()(proofBasket, vatBasisPoints = 1_400),
                        vatBasisPoints = 1_400,
                        lastSaleId = "sale-1",
                        lastReceiptNumber = 412,
                    ),
                    onEvent = {},
                )
            }
        }

        render("till-empty") {
            Page(dark = false) { TillContent(state = TillUiState(), onEvent = {}) }
        }
    }
}

/**
 * Theme, then page.
 *
 * In that order and no other: a `Surface` outside the theme reads the *baseline* colour scheme, so
 * a dark proof came out on Material's lavender-white page with black text on black panels. The
 * screen looked broken; only the proof was.
 */
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

private fun render(name: String, width: Int = 1180, height: Int = 720, content: @Composable () -> Unit) {
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

private val proofBasket = Basket(
    lines = listOf(
        BasketLine(
            variantId = "v1",
            sku = "KSW-TSH-022-NV",
            description = "Round-neck t-shirt — Navy",
            descriptionAr = "تيشيرت رقبة دائرية — كحلي",
            quantity = 2,
            unitPrice = Money.ofPounds(180),
            listPrice = Money.ofPounds(180),
            unitCost = Money.ofPounds(120),
            onHand = 6,
        ),
        BasketLine(
            variantId = "v2",
            sku = "KSW-SHT-004-WH",
            description = "Oxford shirt — White",
            descriptionAr = "قميص أكسفورد — أبيض",
            quantity = 3,
            unitPrice = Money.ofPounds(340),
            listPrice = Money.ofPounds(380),
            unitCost = Money.ofPounds(240),
            lineDiscount = Money.ofPounds(40),
            onHand = 1,
            authorisedByUserId = "admin",
        ),
        BasketLine(
            variantId = "v3",
            sku = "KSW-CHN-011-BG",
            description = "Chino — Beige",
            descriptionAr = "بنطلون تشينو — بيج",
            quantity = 1,
            unitPrice = Money.ofPounds(749),
            listPrice = Money.ofPounds(749),
            unitCost = Money.ofPounds(500),
            onHand = 12,
        ),
    ),
)
