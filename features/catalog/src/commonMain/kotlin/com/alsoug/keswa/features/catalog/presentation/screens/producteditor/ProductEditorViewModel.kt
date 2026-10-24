package com.alsoug.keswa.features.catalog.presentation.screens.producteditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.message
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.features.catalog.domain.usecase.AddColourResult
import com.alsoug.keswa.features.catalog.domain.usecase.AddColourToProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.AssignBarcodeResult
import com.alsoug.keswa.features.catalog.domain.usecase.AssignSupplierBarcodeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GetRetailPriceUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.RemoveColourFromProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.RemoveColourResult
import com.alsoug.keswa.features.catalog.domain.usecase.SetPriceResult
import com.alsoug.keswa.features.catalog.domain.usecase.SetRetailPriceUseCase
import com.alsoug.keswa.features.catalog.presentation.model.toUiModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductEditorViewModel(
    private val products: IProductRepository,
    private val colours: IColourRepository,
    private val variants: IVariantRepository,
    private val addColour: AddColourToProductUseCase,
    private val removeColour: RemoveColourFromProductUseCase,
    private val assignBarcode: AssignSupplierBarcodeUseCase,
    private val setPrice: SetRetailPriceUseCase,
    private val getPrice: GetRetailPriceUseCase,
    private val dispatchers: DispatcherProvider,
    private val arabic: Boolean = false,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductEditorUiState())
    val state: StateFlow<ProductEditorUiState> = _state.asStateFlow()

    private val _navigation = MutableSharedFlow<ProductEditorNavigation>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val navigation = _navigation.asSharedFlow()

    private val _effect = MutableSharedFlow<ProductEditorUiEffect>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val effect = _effect.asSharedFlow()

    private var productId: String? = null

    fun onEvent(event: ProductEditorUiEvent) {
        when (event) {
            is ProductEditorUiEvent.Load -> load(event.productId)
            is ProductEditorUiEvent.AddColour -> add(event.colourId)
            is ProductEditorUiEvent.RemoveColour -> remove(event.variantId)
            is ProductEditorUiEvent.AssignSupplierBarcode ->
                assign(event.variantId, event.barcode)
            is ProductEditorUiEvent.SetPrice -> price(event.variantId, event.amount)
            is ProductEditorUiEvent.Back ->
                viewModelScope.launch { _navigation.emit(ProductEditorNavigation.Back) }
        }
    }

    private fun load(id: String) {
        productId = id
        viewModelScope.launch(dispatchers.io) {
            _state.update { it.copy(isLoading = true) }
            runCatching {
                val product = products.getById(id).getOrThrow()
                    ?: error("product not found: $id")
                val palette = colours.getAll().getOrThrow()
                val rows = variants.forProduct(id).getOrThrow()
                    .filter { it.isActive }
                    .mapNotNull { variant ->
                        val colour = palette.firstOrNull { it.id == variant.colourId }
                            ?: return@mapNotNull null
                        variant.toUiModel(
                            colour = colour,
                            arabic = arabic,
                            barcode = variants.barcodesFor(variant.id).getOrThrow()
                                .firstOrNull { it.isPrimary }?.barcode,
                            onHand = variants.onHand(variant.id).getOrThrow(),
                            price = getPrice(variant.id).getOrThrow(),
                        )
                    }
                Triple(if (arabic) product.nameAr else product.name, rows, palette)
            }.fold(
                onSuccess = { (label, rows, palette) ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            productLabel = label,
                            colours = rows,
                            availableColours = palette,
                        )
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun add(colourId: String) {
        val id = productId ?: return
        viewModelScope.launch(dispatchers.io) {
            addColour(id, colourId).fold(
                onSuccess = { result ->
                    when (result) {
                        is AddColourResult.Added -> {
                            _effect.emit(
                                ProductEditorUiEffect.ShowMessage(message { it.skuCreated(result.variant.sku) }),
                            )
                            load(id)
                        }
                        AddColourResult.AlreadyStocked ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.colourAlreadyStocked }))
                        AddColourResult.ProductNotFound ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.productNotFound }))
                        AddColourResult.ColourNotFound ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.colourNotFound }))
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun remove(variantId: String) {
        val id = productId ?: return
        viewModelScope.launch(dispatchers.io) {
            removeColour(variantId).fold(
                onSuccess = { result ->
                    when (result) {
                        RemoveColourResult.Removed -> {
                            _effect.emit(ProductEditorUiEffect.ShowMessage(message { it.colourRetired }))
                            load(id)
                        }
                        is RemoveColourResult.HasStock ->
                            _effect.emit(ProductEditorUiEffect.BlockedByStock(result.onHand))
                        RemoveColourResult.NotFound ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.colourNotFound }))
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun assign(variantId: String, barcode: String) {
        val id = productId ?: return
        viewModelScope.launch(dispatchers.io) {
            assignBarcode(variantId, barcode).fold(
                onSuccess = { result ->
                    when (result) {
                        AssignBarcodeResult.Assigned -> {
                            _effect.emit(ProductEditorUiEffect.ShowMessage(message { it.barcodeAttached }))
                            load(id)
                        }
                        AssignBarcodeResult.AlreadyInUse ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.barcodeAlreadyInUse }))
                        AssignBarcodeResult.NotAnEan13 ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.notValidEanThirteen }))
                        AssignBarcodeResult.VariantNotFound ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.colourNotFound }))
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private fun price(variantId: String, amount: String) {
        val id = productId ?: return
        viewModelScope.launch(dispatchers.io) {
            setPrice(variantId, amount).fold(
                onSuccess = { result ->
                    when (result) {
                        SetPriceResult.Saved -> {
                            _effect.emit(ProductEditorUiEffect.ShowMessage(message { it.priceSet }))
                            load(id)
                        }
                        SetPriceResult.NotAnAmount ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.notAnAmount }))
                        SetPriceResult.NoPriceList ->
                            _effect.emit(ProductEditorUiEffect.ShowError(message { it.noRetailPriceList }))
                    }
                },
                onFailure = { fail(it) },
            )
        }
    }

    private suspend fun fail(cause: Throwable) {
        _state.update { it.copy(isLoading = false) }
        _effect.emit(ProductEditorUiEffect.ShowError(message { it.somethingWentWrong }))
    }
}
