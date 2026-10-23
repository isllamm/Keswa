package com.alsoug.keswa.features.inventory.presentation.screens.receiving

import com.alsoug.keswa.core.designsystem.Message
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.model.StockReceipt
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.CostChange
import com.alsoug.keswa.features.inventory.domain.usecase.EntryProblem

/** One level walked down in the picker. An empty list of crumbs is the top level. */
data class CategoryCrumb(val id: String, val name: String, val nameAr: String)

/** What has been typed against one colour — kept by variant id, so moving category loses nothing. */
data class TypedEntry(val item: SellableItem, val quantity: String, val cost: String, val price: String)

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
    val sellPrice: Money? = null,
    val livePrice: Money? = null,
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
    val priceEntry: String = "",
    val crumbs: List<CategoryCrumb> = emptyList(),
    val subCategories: List<Category> = emptyList(),
    val levelItems: List<SellableItem> = emptyList(),
    val typed: Map<String, TypedEntry> = emptyMap(),
    /** Rows the last "Add" refused, like the import's problems. Cleared as each row is changed. */
    val problems: Map<String, EntryProblem> = emptyMap(),
    val isAddingLines: Boolean = false,
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

    val typedCount: Int get() = typed.values.count { it.quantity.isNotBlank() }

    val canAddLines: Boolean get() = isDraft && typedCount > 0 && !isAddingLines
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
    data class PriceChanged(val value: String) : ReceivingUiEvent
    data object ConfirmLine : ReceivingUiEvent
    data object CancelLine : ReceivingUiEvent
    data class RemoveLine(val lineId: String) : ReceivingUiEvent
    data object Post : ReceivingUiEvent
    data object Discard : ReceivingUiEvent
    data object PrintTags : ReceivingUiEvent
    data class GenerateBarcode(val variantId: String) : ReceivingUiEvent
    data class PrintLineLabels(val lineId: String, val copies: Int) : ReceivingUiEvent
    data class Open(val receiptId: String) : ReceivingUiEvent
    data class OpenCategory(val categoryId: String) : ReceivingUiEvent
    data class GoUpTo(val depth: Int) : ReceivingUiEvent
    data class PickQuantityChanged(val variantId: String, val value: String) : ReceivingUiEvent
    data class PickCostChanged(val variantId: String, val value: String) : ReceivingUiEvent
    data class PickPriceChanged(val variantId: String, val value: String) : ReceivingUiEvent
    data object AddPicked : ReceivingUiEvent
    data object ClearPicked : ReceivingUiEvent
}

sealed interface ReceivingNavigation {
    data object Done : ReceivingNavigation
}

sealed interface ReceivingUiEffect {
    data class ShowError(val message: Message) : ReceivingUiEffect
    data class ShowMessage(val message: Message) : ReceivingUiEffect
}
