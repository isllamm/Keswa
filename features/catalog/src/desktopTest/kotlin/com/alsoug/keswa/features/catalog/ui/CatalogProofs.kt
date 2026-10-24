package com.alsoug.keswa.features.catalog.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.alsoug.keswa.core.designsystem.KeswaLanguage
import com.alsoug.keswa.core.designsystem.KeswaTheme
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.catalog.presentation.model.CategoryNodeUiModel
import com.alsoug.keswa.features.catalog.presentation.model.ColourRowUiModel
import com.alsoug.keswa.features.catalog.presentation.model.ProductUiModel
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserContent
import com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser.CatalogBrowserUiState
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorContent
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorUiState
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/**
 * Visual regression proofs for Catalog Browser and Product Variant Editor.
 *
 * Renders into `features/catalog/build/ui-proofs/`.
 */
class CatalogProofs {

    private val categories = listOf(
        CategoryNodeUiModel("1", "T-shirts", depth = 0, hasChildren = true),
        CategoryNodeUiModel("2", "Round neck", depth = 1, hasChildren = false),
        CategoryNodeUiModel("3", "Shirts", depth = 0, hasChildren = false),
        CategoryNodeUiModel("4", "Trousers", depth = 0, hasChildren = false),
        CategoryNodeUiModel("5", "Archived", depth = 0, hasChildren = false, isActive = false),
    )

    private val products = listOf(
        ProductUiModel("p1", "Round-neck t-shirt", colourCount = 5),
        ProductUiModel("p2", "V-neck t-shirt", colourCount = 3),
        ProductUiModel("p3", "Polo short sleeve", colourCount = 4),
    )

    private val colours = listOf(
        ColourRowUiModel("v1", "c1", "Navy", "#20304f", "OXF-NAV", "2000000000015", 12, Money.ofPounds(380)),
        ColourRowUiModel("v2", "c2", "White", "#f2f2ef", "OXF-WHI", "2000000000022", 5, Money.ofPounds(380)),
        ColourRowUiModel("v3", "c3", "Burgundy", "#6b1724", "OXF-BUR", "2000000000039", 0, null),
    )

    private val addableColours = listOf(
        Colour("c4", "Sky Blue", "أزرق سماوي", "#87ceeb", 3, true),
        Colour("c5", "Olive", "زيتوني", "#556b2f", 4, true),
        Colour("c6", "Black", "أسود", "#111111", 5, true),
    )

    @Test
    fun `render all catalog browser states`() {
        render("catalog-browser-success") {
            Page(dark = false) {
                CatalogBrowserContent(
                    state = CatalogBrowserUiState(
                        categories = categories,
                        selectedCategoryId = "2",
                        products = products,
                    ),
                    onEvent = {},
                )
            }
        }

        render("catalog-browser-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                CatalogBrowserContent(
                    state = CatalogBrowserUiState(
                        categories = categories,
                        selectedCategoryId = "2",
                        products = products,
                    ),
                    onEvent = {},
                )
            }
        }

        render("catalog-browser-dark") {
            Page(dark = true) {
                CatalogBrowserContent(
                    state = CatalogBrowserUiState(
                        categories = categories,
                        selectedCategoryId = "2",
                        products = products,
                    ),
                    onEvent = {},
                )
            }
        }

        render("catalog-browser-empty") {
            Page(dark = false) {
                CatalogBrowserContent(
                    state = CatalogBrowserUiState(
                        categories = categories,
                        selectedCategoryId = "3",
                    ),
                    onEvent = {},
                )
            }
        }
    }

    @Test
    fun `render all product editor states`() {
        render("product-editor-success") {
            Page(dark = false) {
                ProductEditorContent(
                    state = ProductEditorUiState(
                        productLabel = "Oxford shirt",
                        colours = colours,
                        availableColours = addableColours,
                    ),
                    onEvent = {},
                )
            }
        }

        render("product-editor-arabic") {
            Page(dark = false, language = KeswaLanguage.ARABIC) {
                ProductEditorContent(
                    state = ProductEditorUiState(
                        productLabel = "قميص أوكسفورد",
                        colours = colours,
                        availableColours = addableColours,
                    ),
                    onEvent = {},
                )
            }
        }

        render("product-editor-dark") {
            Page(dark = true) {
                ProductEditorContent(
                    state = ProductEditorUiState(
                        productLabel = "Oxford shirt",
                        colours = colours,
                        availableColours = addableColours,
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
