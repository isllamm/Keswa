package com.alsoug.keswa.features.catalog.presentation

import com.alsoug.keswa.core.coroutines.DispatcherProvider
import com.alsoug.keswa.core.designsystem.EnglishStrings
import com.alsoug.keswa.core.designsystem.resolve
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.catalog.domain.usecase.AddColourToProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.AssignSupplierBarcodeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.FakeColourRepository
import com.alsoug.keswa.features.catalog.domain.usecase.FakePriceRepository
import com.alsoug.keswa.features.catalog.domain.usecase.FakeProductRepository
import com.alsoug.keswa.features.catalog.domain.usecase.FakeVariantRepository
import com.alsoug.keswa.features.catalog.domain.usecase.GenerateInternalBarcodeUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.GetRetailPriceUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.RemoveColourFromProductUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.SequentialIds
import com.alsoug.keswa.features.catalog.domain.usecase.SetRetailPriceUseCase
import com.alsoug.keswa.features.catalog.domain.usecase.adminSession
import com.alsoug.keswa.features.catalog.domain.usecase.colour
import com.alsoug.keswa.features.catalog.domain.usecase.product
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorUiEffect
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorUiEvent
import com.alsoug.keswa.features.catalog.presentation.screens.producteditor.ProductEditorViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * KD-003 pays off here: because the ViewModel takes its dispatchers rather than reaching for
 * [Dispatchers], `runTest` controls its virtual time and no `runBlocking` workaround is needed —
 * the flakiness logged as defect T2 in the cashi_pax review.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductEditorViewModelTest {

    private val scheduler = StandardTestDispatcher()

    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = scheduler
        override val main: CoroutineDispatcher = scheduler
        override val default: CoroutineDispatcher = scheduler
    }

    private val products = FakeProductRepository().given(product())
    private val colours = FakeColourRepository()
        .given(colour("c1", "Navy"))
        .given(colour("c2", "Red"))
    private val variants = FakeVariantRepository()
    private val prices = FakePriceRepository()

    private fun viewModel() = ProductEditorViewModel(
        products = products,
        colours = colours,
        variants = variants,
        addColour = AddColourToProductUseCase(
            products, colours, variants, GenerateInternalBarcodeUseCase(variants), SequentialIds("v"),
        ),
        removeColour = RemoveColourFromProductUseCase(variants),
        assignBarcode = AssignSupplierBarcodeUseCase(variants),
        setPrice = SetRetailPriceUseCase(prices, adminSession(), SequentialIds("price")) { 0 },
        getPrice = GetRetailPriceUseCase(prices) { 0 },
        dispatchers = dispatchers,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(scheduler)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loading a product populates state without touching the effect channel`() = runTest(scheduler) {
        val viewModel = viewModel()
        val effects = mutableListOf<ProductEditorUiEffect>()
        backgroundScope.launch { viewModel.effect.collect { effects += it } }

        viewModel.onEvent(ProductEditorUiEvent.Load("p1"))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("Oxford shirt", state.productLabel)
        assertTrue(!state.isLoading)
        assertEquals(2, state.addableColours.size)
        assertTrue(effects.isEmpty(), "a plain load should emit nothing")
    }

    @Test
    fun `adding a colour updates state and announces the new SKU`() = runTest(scheduler) {
        val viewModel = viewModel()
        val effects = mutableListOf<ProductEditorUiEffect>()
        backgroundScope.launch { viewModel.effect.collect { effects += it } }

        viewModel.onEvent(ProductEditorUiEvent.Load("p1"))
        advanceUntilIdle()
        viewModel.onEvent(ProductEditorUiEvent.AddColour("c1"))
        advanceUntilIdle()

        // State carries the new row, and one colour is no longer offered
        val state = viewModel.state.value
        assertEquals(1, state.colours.size)
        assertEquals("OXF-NAV", state.colours.single().sku)
        assertEquals(1, state.addableColours.size)

        // The confirmation is an effect, not a flag in state (ADR-030)
        val message = assertIs<ProductEditorUiEffect.ShowMessage>(effects.single())
        assertTrue(message.message.resolve(EnglishStrings).contains("OXF-NAV"))
    }

    @Test
    fun `retiring a colour with stock emits BlockedByStock and changes nothing`() = runTest(scheduler) {
        val viewModel = viewModel()
        val effects = mutableListOf<ProductEditorUiEffect>()
        backgroundScope.launch { viewModel.effect.collect { effects += it } }

        viewModel.onEvent(ProductEditorUiEvent.Load("p1"))
        advanceUntilIdle()
        viewModel.onEvent(ProductEditorUiEvent.AddColour("c1"))
        advanceUntilIdle()
        val variantId = viewModel.state.value.colours.single().variantId
        variants.givenStock(variantId, 7)

        val next = async { viewModel.effect.first() }
        runCurrent()
        viewModel.onEvent(ProductEditorUiEvent.RemoveColour(variantId))
        advanceUntilIdle()

        assertEquals(ProductEditorUiEffect.BlockedByStock(7), next.await())
        assertEquals(1, viewModel.state.value.colours.size)
    }

    @Test
    fun `an invalid supplier barcode is rejected as an effect`() = runTest(scheduler) {
        val viewModel = viewModel()
        val effects = mutableListOf<ProductEditorUiEffect>()
        backgroundScope.launch { viewModel.effect.collect { effects += it } }

        viewModel.onEvent(ProductEditorUiEvent.Load("p1"))
        advanceUntilIdle()
        viewModel.onEvent(ProductEditorUiEvent.AddColour("c1"))
        advanceUntilIdle()
        val variantId = viewModel.state.value.colours.single().variantId

        val next = async { viewModel.effect.first() }
        runCurrent()
        viewModel.onEvent(ProductEditorUiEvent.AssignSupplierBarcode(variantId, "5901234123458"))
        advanceUntilIdle()

        val error = assertIs<ProductEditorUiEffect.ShowError>(next.await())
        assertTrue(error.message.resolve(EnglishStrings).contains("EAN-13"))
    }
}
