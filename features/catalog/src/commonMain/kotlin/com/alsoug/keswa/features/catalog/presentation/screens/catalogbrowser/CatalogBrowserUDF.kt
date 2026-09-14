package com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser

import com.alsoug.keswa.features.catalog.presentation.model.CategoryNodeUiModel
import com.alsoug.keswa.features.catalog.presentation.model.ProductUiModel

/**
 * Persistent screen state only.
 *
 * ADR-030: no dialog or snackbar flags live here. Anything transient is an effect, and the screen
 * holds its visibility in `remember`.
 */
data class CatalogBrowserUiState(
    val isLoading: Boolean = false,
    val categories: List<CategoryNodeUiModel> = emptyList(),
    val selectedCategoryId: String? = null,
    val products: List<ProductUiModel> = emptyList(),
    val search: String = "",
) {
    val selectedCategory: CategoryNodeUiModel?
        get() = categories.firstOrNull { it.id == selectedCategoryId }

    val canAddSubCategory: Boolean get() = selectedCategoryId != null

    val isEmpty: Boolean get() = !isLoading && products.isEmpty()
}

sealed interface CatalogBrowserUiEvent {
    data object Load : CatalogBrowserUiEvent
    data class CategorySelected(val categoryId: String) : CatalogBrowserUiEvent
    data class SearchChanged(val query: String) : CatalogBrowserUiEvent
    data class CreateCategory(val name: String, val nameAr: String, val underSelected: Boolean) :
        CatalogBrowserUiEvent
    data class MoveCategory(val categoryId: String, val newParentId: String?) :
        CatalogBrowserUiEvent
    data class CreateProduct(val name: String, val nameAr: String) : CatalogBrowserUiEvent
    data class ProductSelected(val productId: String) : CatalogBrowserUiEvent
}

sealed interface CatalogBrowserNavigation {
    data class ToProductEditor(val productId: String) : CatalogBrowserNavigation
}

sealed interface CatalogBrowserUiEffect {
    data class ShowError(val message: String) : CatalogBrowserUiEffect
    data class ShowMessage(val message: String) : CatalogBrowserUiEffect
}
