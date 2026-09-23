package com.alsoug.keswa.features.catalog.presentation.screens.catalogbrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.features.catalog.domain.usecase.CreateCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.CreateProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GetCategoryTreeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GetProductsInCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.MoveCategoryUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.SearchCatalogUseCase
import com.alsoug.keswa.features.catalog.presentation.model.toUiModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ADR-021: holds state and delegates. Every rule it applies lives in a use case.
 */
class CatalogBrowserViewModel(
    private val getTree: GetCategoryTreeUseCase,
    private val getProducts: GetProductsInCategoryUseCase,
    private val createCategory: CreateCategoryUseCase,
    private val moveCategory: MoveCategoryUseCase,
    private val createProduct: CreateProductUseCase,
    private val search: SearchCatalogUseCase,
    private val dispatchers: DispatcherProvider,
    private val arabic: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogBrowserUiState())
    val state: StateFlow<CatalogBrowserUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<CatalogBrowserNavigation>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<CatalogBrowserUiEffect>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val effect = _effect.asSharedFlow()

    fun onEvent(event: CatalogBrowserUiEvent) {
        when (event) {
            is CatalogBrowserUiEvent.Load -> load()
            is CatalogBrowserUiEvent.CategorySelected -> selectCategory(event.categoryId)
            is CatalogBrowserUiEvent.SearchChanged -> runSearch(event.query)
            is CatalogBrowserUiEvent.CreateCategory -> addCategory(event)
            is CatalogBrowserUiEvent.MoveCategory -> relocate(event.categoryId, event.newParentId)
            is CatalogBrowserUiEvent.CreateProduct -> addProduct(event.name, event.nameAr)
            is CatalogBrowserUiEvent.ProductSelected -> openProduct(event.productId)
        }
    }

    private fun load() {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            getTree().fold(
                onSuccess = { categories ->
                    val nodes = categories.toNodes()
                    _state.update {
                        it.copy(
                            isLoading = false,
                            categories = nodes,
                            selectedCategoryId = it.selectedCategoryId ?: nodes.firstOrNull()?.id,
                        )
                    }
                    _state.value.selectedCategoryId?.let { selectCategory(it) }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun selectCategory(categoryId: String) {
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(selectedCategoryId = categoryId, isLoading = true) }
            getProducts(categoryId).fold(
                onSuccess = { products ->
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            products = products.map { it.toUiModel(arabic, colourCount = 0) },
                        )
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun runSearch(query: String) {
        _state.update { it.copy(search = query) }
        val categoryId = _state.value.selectedCategoryId ?: return
        if (query.isBlank()) {
            selectCategory(categoryId)
            return
        }
        viewModelScope.launch(dispatchers.io) {
            search(query).fold(
                onSuccess = { products ->
                    _state.update { current ->
                        current.copy(products = products.map { it.toUiModel(arabic, 0) })
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun addCategory(event: CatalogBrowserUiEvent.CreateCategory) {
        val parentId = if (event.underSelected) _state.value.selectedCategoryId else null
        viewModelScope.launch(dispatchers.io) {
            createCategory(parentId, event.name, event.nameAr).fold(
                onSuccess = {
                    _effect.emit(CatalogBrowserUiEffect.ShowMessage(message { it.categoryAdded }))
                    load()
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun relocate(categoryId: String, newParentId: String?) {
        viewModelScope.launch(dispatchers.io) {
            moveCategory(categoryId, newParentId).fold(
                onSuccess = { load() },
                onFailure = { fail(it) },
            )
        }
    }

    private fun addProduct(name: String, nameAr: String) {
        val categoryId = _state.value.selectedCategoryId ?: run {
            viewModelScope.launch {
                _effect.emit(CatalogBrowserUiEffect.ShowError(message { it.pickACategoryFirst }))
            }
            return
        }
        viewModelScope.launch(dispatchers.io) {
            createProduct(name, nameAr, categoryId).fold(
                onSuccess = { product ->
                    selectCategory(categoryId)
                    _navigation.emit(CatalogBrowserNavigation.ToProductEditor(product.id))
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun openProduct(productId: String) {
        viewModelScope.launch {
            _navigation.emit(CatalogBrowserNavigation.ToProductEditor(productId))
        }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(CatalogBrowserUiEffect.ShowError(message { it.somethingWentWrong }))
    }

    /** A node has children when some other node's path sits directly beneath its own. */
    private fun List<Category>.toNodes() = map { category ->
        category.toUiModel(
            arabic = arabic,
            hasChildren = any { it.parentId == category.id },
        )
    }
}
