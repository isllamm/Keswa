package com.alsoug.keswa.features.catalog.presentation.screens.producteditor

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.features.catalog.presentation.model.ColourRowUiModel

data class ProductEditorUiState(
    val isLoading: Boolean = false,
    val productLabel: String = "",
    val colours: List<ColourRowUiModel> = emptyList(),
    val availableColours: List<Colour> = emptyList(),
) {
    val totalOnHand: Int get() = colours.sumOf { it.onHand }

    /** Colours not yet stocked for this product — the only ones worth offering. */
    val addableColours: List<Colour>
        get() = availableColours.filterNot { candidate ->
            colours.any { it.colourId == candidate.id }
        }
}

sealed interface ProductEditorUiEvent {
    data class Load(val productId: String) : ProductEditorUiEvent
    data class AddColour(val colourId: String) : ProductEditorUiEvent
    data class RemoveColour(val variantId: String) : ProductEditorUiEvent
    data class AssignSupplierBarcode(val variantId: String, val barcode: String) :
        ProductEditorUiEvent
    data class SetPrice(val variantId: String, val amount: String) : ProductEditorUiEvent
    data object Back : ProductEditorUiEvent
}

sealed interface ProductEditorNavigation {
    data object Back : ProductEditorNavigation
}

sealed interface ProductEditorUiEffect {
    data class ShowError(val message: Message) : ProductEditorUiEffect
    data class ShowMessage(val message: Message) : ProductEditorUiEffect
    /** The colour could not be removed; the screen explains why rather than failing silently. */
    data class BlockedByStock(val onHand: Int) : ProductEditorUiEffect
}
