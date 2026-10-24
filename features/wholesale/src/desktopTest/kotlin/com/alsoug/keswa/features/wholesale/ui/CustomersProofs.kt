package com.alsoug.keswa.features.wholesale.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.Ageing
import com.alsoug.keswa.core.domain.model.Customer
import com.alsoug.keswa.core.domain.model.LedgerEntry
import com.alsoug.keswa.core.domain.model.LedgerEntryType
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomerRowUiModel
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomersContent
import com.alsoug.keswa.features.wholesale.presentation.screens.customers.CustomersUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Visual regression proofs for Wholesale Customer Management and Ledger.
 *
 * Renders into `features/wholesale/build/ui-proofs/`.
 */
class CustomersProofs {

    private val sampleCustomer = Customer(
        id = "cus-1",
        name = "Nasr Textiles",
        nameAr = "نصر للنسيج",
        phone = "0100 000 0000",
        taxId = "123-456-789",
        priceListId = "pricelist-trade",
        creditLimit = Money.ofPounds(50_000),
        paymentTermsDays = 30,
        isActive = true,
    )

    private val sampleRows = listOf(
        CustomerRowUiModel(
            customer = sampleCustomer,
            balance = Money.ofPounds(14_200),
            available = Money.ofPounds(35_800),
            isOverdue = true,
        ),
        CustomerRowUiModel(
            customer = sampleCustomer.copy(
                id = "cus-2",
                name = "Downtown Boutique",
                nameAr = "بوتيك وسط البلد",
                creditLimit = Money.ZERO,
            ),
            balance = Money.ZERO,
            available = Money.ZERO,
            isOverdue = false,
        ),
    )

    private val sampleEntries = listOf(
        LedgerEntry("e1", "cus-1", LedgerEntryType.INVOICE, Money.ofPounds(18_400), "INVOICE", "s1", 0, 0, "u1", null),
        LedgerEntry("e2", "cus-1", LedgerEntryType.PAYMENT, Money.ofPounds(-4_200), null, null, 1, null, "u1", "Cash payment received"),
    )

    private val populatedState = CustomersUiState(
        customers = sampleRows,
        selected = sampleCustomer,
        balance = Money.ofPounds(14_200),
        available = Money.ofPounds(35_800),
        ageing = Ageing(
            current = Money.ofPounds(8_000),
            thirtyDays = Money.ofPounds(6_200),
            sixtyDays = Money.ZERO,
            ninetyDaysPlus = Money.ZERO,
        ),
        entries = sampleEntries,
    )

    @Test
    fun `render all customer screen states`() {
        render("customers-success") {
            Page(dark = false) {
                CustomersContent(state = populatedState, onEvent = {})
            }
        }

        render("customers-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                CustomersContent(state = populatedState, onEvent = {})
            }
        }

        render("customers-dark") {
            Page(dark = true) {
                CustomersContent(state = populatedState, onEvent = {})
            }
        }

        render("customers-empty") {
            Page(dark = false) {
                CustomersContent(state = CustomersUiState(), onEvent = {})
            }
        }

        render("customers-new-dialog") {
            Page(dark = false) {
                CustomersContent(
                    state = CustomersUiState(
                        customers = sampleRows,
                        isCreating = true,
                        newName = "Delta Fabrics",
                        newPhone = "0112 345 6789",
                        newLimit = "25000",
                        newTerms = "45",
                    ),
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
