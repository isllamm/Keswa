package keswa.feature.catalog

import keswa.core.common.AppResult
import keswa.core.common.parseMoneyInput
import keswa.core.ui.mvi.MviStore
import keswa.domain.PrincipalHolder
import keswa.domain.require
import keswa.domain.catalog.CreateProductWithVariants
import keswa.domain.catalog.NewProductRequest
import keswa.domain.catalog.NewProductVariantRequest
import keswa.domain.catalog.OptionInput
import keswa.domain.catalog.OptionValueInput
import keswa.domain.catalog.ProductRepository
import keswa.domain.catalog.VariantMatrix
import keswa.feature.catalog.CatalogContract.Effect
import keswa.feature.catalog.CatalogContract.Intent
import keswa.feature.catalog.CatalogContract.State
import keswa.feature.catalog.CatalogContract.VariantRow

class CatalogStore(
    private val products: ProductRepository,
    private val createProduct: CreateProductWithVariants,
    private val principalHolder: PrincipalHolder,
) : MviStore<State, Intent, Effect>(State()) {

    override fun reduce(state: State, intent: Intent): State = when (intent) {
        is Intent.ScreenEntered -> state.copy(isLoadingList = true)
        is Intent.SearchChanged -> state.copy(searchQuery = intent.query, isLoadingList = true)

        is Intent.AddProductClicked -> state.copy(
            isCreating = true, nameAr = "", nameEn = "", sizeValues = "", colorValues = "",
            variantRows = emptyList(), error = null,
        )
        is Intent.CancelCreateClicked -> state.copy(isCreating = false, error = null)

        is Intent.NameArChanged -> state.copy(nameAr = intent.value)
        is Intent.NameEnChanged -> state.copy(nameEn = intent.value)
        is Intent.SizeValuesChanged -> state.copy(sizeValues = intent.value)
        is Intent.ColorValuesChanged -> state.copy(colorValues = intent.value)

        is Intent.GenerateVariantsClicked -> {
            val options = buildOptions(state.sizeValues, state.colorValues)
            when (val result = VariantMatrix.generate(options)) {
                is AppResult.Ok -> state.copy(
                    variantRows = result.value.map { VariantRow(it.optionValues, it.nameSuffix) },
                    error = null,
                )
                is AppResult.Err -> state.copy(error = result.error.key)
            }
        }

        is Intent.VariantCostChanged -> state.withRow(intent.index) { it.copy(costText = intent.value) }
        is Intent.VariantQtyChanged -> state.withRow(intent.index) { it.copy(qtyText = intent.value) }
        is Intent.VariantBarcodeChanged -> state.withRow(intent.index) { it.copy(barcodeText = intent.value) }

        is Intent.SaveClicked -> if (state.isSaving) state else state.copy(isSaving = true, error = null)
        is Intent.ErrorDismissed -> state.copy(error = null)

        is Intent.Internal.ProductsLoaded -> state.copy(isLoadingList = false, products = intent.products)
        is Intent.Internal.SaveSucceeded -> State(isLoadingList = false, products = intent.products)
        is Intent.Internal.SaveFailed -> state.copy(isSaving = false, error = intent.error)
    }

    override suspend fun handle(intent: Intent, state: State) {
        when (intent) {
            is Intent.ScreenEntered, is Intent.SearchChanged -> {
                val query = if (intent is Intent.SearchChanged) intent.query else state.searchQuery
                dispatch(Intent.Internal.ProductsLoaded(products.search(query)))
            }

            is Intent.SaveClicked -> {
                if (state.isSaving) return // pre-reduction state — see MviStore's kdoc
                val request = NewProductRequest(
                    code = null,
                    nameAr = state.nameAr,
                    nameEn = state.nameEn.trim().takeIf(String::isNotEmpty),
                    categoryId = null,
                    brandId = null,
                    taxRateBp = 0, // ASSUMPTION: no tax charged yet — architecture.md §Catalog
                    currencyCode = "EGP", // ASSUMPTION: single currency in Phase 0 — matches Seeder
                    variants = state.variantRows.map { it.toRequest() },
                )
                when (val result = createProduct(principalHolder.require(), request)) {
                    is AppResult.Ok -> {
                        dispatch(Intent.Internal.SaveSucceeded(products.search("")))
                        emit(Effect.ProductSaved)
                    }
                    is AppResult.Err -> dispatch(Intent.Internal.SaveFailed(result.error.key))
                }
            }

            else -> Unit
        }
    }

    private fun State.withRow(index: Int, transform: (VariantRow) -> VariantRow) = copy(
        variantRows = variantRows.mapIndexed { i, row -> if (i == index) transform(row) else row },
    )

    private fun VariantRow.toRequest() = NewProductVariantRequest(
        optionValues = optionValues,
        nameSuffix = nameSuffix,
        costMinor = parseMoneyInput(costText) ?: 0,
        openingQty = qtyText.trim().toIntOrNull() ?: 0,
        barcodeCode = barcodeText.trim().takeIf(String::isNotEmpty),
    )

    private fun buildOptions(sizeValues: String, colorValues: String): List<OptionInput> = buildList {
        parseCsv(sizeValues).takeIf { it.isNotEmpty() }?.let { add(OptionInput("Size", values = it.map(::OptionValueInput))) }
        parseCsv(colorValues).takeIf { it.isNotEmpty() }?.let { add(OptionInput("Colour", values = it.map(::OptionValueInput))) }
    }

    private fun parseCsv(text: String): List<String> = text.split(',').map(String::trim).filter(String::isNotEmpty)
}
