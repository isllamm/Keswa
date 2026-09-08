package keswa.feature.catalog

import keswa.core.common.ErrorKey
import keswa.core.ui.mvi.MviEffect
import keswa.core.ui.mvi.MviIntent
import keswa.core.ui.mvi.MviState
import keswa.domain.catalog.OptionValueInput
import keswa.domain.catalog.ProductWithVariants

object CatalogContract {

    data class VariantRow(
        val optionValues: List<OptionValueInput>,
        val nameSuffix: String,
        val costText: String = "",
        val qtyText: String = "",
        val barcodeText: String = "",
    )

    data class State(
        val isLoadingList: Boolean = true,
        val products: List<ProductWithVariants> = emptyList(),
        val searchQuery: String = "",
        val isCreating: Boolean = false,
        val nameAr: String = "",
        val nameEn: String = "",
        val sizeValues: String = "",
        val colorValues: String = "",
        val variantRows: List<VariantRow> = emptyList(),
        val isSaving: Boolean = false,
        val error: ErrorKey? = null,
    ) : MviState

    sealed interface Intent : MviIntent {
        data object ScreenEntered : Intent
        data class SearchChanged(val query: String) : Intent
        data object AddProductClicked : Intent
        data object CancelCreateClicked : Intent
        data class NameArChanged(val value: String) : Intent
        data class NameEnChanged(val value: String) : Intent
        data class SizeValuesChanged(val value: String) : Intent
        data class ColorValuesChanged(val value: String) : Intent
        data object GenerateVariantsClicked : Intent
        data class VariantCostChanged(val index: Int, val value: String) : Intent
        data class VariantQtyChanged(val index: Int, val value: String) : Intent
        data class VariantBarcodeChanged(val index: Int, val value: String) : Intent
        data object SaveClicked : Intent
        data object ErrorDismissed : Intent

        sealed interface Internal : Intent {
            data class ProductsLoaded(val products: List<ProductWithVariants>) : Internal
            data class SaveSucceeded(val products: List<ProductWithVariants>) : Internal
            data class SaveFailed(val error: ErrorKey) : Internal
        }
    }

    sealed interface Effect : MviEffect {
        data object ProductSaved : Effect
    }
}
