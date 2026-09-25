package com.alsoug.keswa.features.inventory.presentation.screens.receiving

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.CostChange

/** A line as the screen shows it: the receipt's own numbers plus who the variant is. */
data class ReceiptLineUiModel(
    val lineId: String,
    val variantId: String,
    val sku: String,
    val description: String,
    val descriptionAr: String = "",
    val quantity: Int,
    val unitCost: Money,
    val lineTotal: Money,
    val barcode: String? = null,
)

data class ReceivingUiState(
    val isLoading: Boolean = false,
    val isPosting: Boolean = false,
    val receipt: StockReceipt? = null,
    val lines: List<ReceiptLineUiModel> = emptyList(),
    val reference: String = "",
    val supplierName: String = "",
    val scanEntry: String = "",
    val pendingItem: SellableItem? = null,
    val quantityEntry: String = "",
    val costEntry: String = "",
    val recent: List<StockReceipt> = emptyList(),
    /**
     * Shown after a post, not before.
     *
     * A supplier quietly raising a price is what an owner wants to be told, and this is the moment
     * it becomes visible.
     */
    val costChanges: List<CostChange> = emptyList(),
) {
    val isDraft: Boolean get() = receipt?.isDraft == true

    val pieceCount: Int get() = lines.sumOf { it.quantity }

    val totalCost: Money get() = lines.fold(Money.ZERO) { sum, line -> sum + line.lineTotal }

    val canPost: Boolean get() = isDraft && lines.isNotEmpty() && !isPosting
}

sealed interface ReceivingUiEvent {
    data object Load : ReceivingUiEvent
    data class ReferenceChanged(val value: String) : ReceivingUiEvent
    data class SupplierChanged(val value: String) : ReceivingUiEvent
    data object StartReceipt : ReceivingUiEvent
    data class ScanEntryChanged(val value: String) : ReceivingUiEvent
    data class Scanned(val barcode: String) : ReceivingUiEvent
    data class QuantityChanged(val value: String) : ReceivingUiEvent
    data class CostChanged(val value: String) : ReceivingUiEvent
    data object ConfirmLine : ReceivingUiEvent
    data object CancelLine : ReceivingUiEvent
    data class RemoveLine(val lineId: String) : ReceivingUiEvent
    data object Post : ReceivingUiEvent
    data object Discard : ReceivingUiEvent
    data object PrintTags : ReceivingUiEvent
    data class GenerateBarcode(val variantId: String) : ReceivingUiEvent
    data class PrintVariantTag(val variantId: String) : ReceivingUiEvent
    data class Open(val receiptId: String) : ReceivingUiEvent
}

sealed interface ReceivingNavigation {
    data object Done : ReceivingNavigation
}

sealed interface ReceivingUiEffect {
    data class ShowError(val message: Message) : ReceivingUiEffect
    data class ShowMessage(val message: Message) : ReceivingUiEffect
}
