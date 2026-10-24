package com.alsoug.keswa.features.inventory.presentation

import com.alsoug.keswa.core.domain.IdGenerator
import com.alsoug.keswa.core.domain.model.Category
import com.alsoug.keswa.core.domain.model.Colour
import com.alsoug.keswa.core.domain.model.Product
import com.alsoug.keswa.core.domain.model.Variant
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.core.domain.repository.ICategoryRepository
import com.alsoug.keswa.core.domain.repository.IColourRepository
import com.alsoug.keswa.core.domain.repository.IProductRepository
import com.alsoug.keswa.core.domain.repository.IVariantRepository
import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ApplyCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ParseCatalogueImportUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartReceiptUseCase
import com.alsoug.keswa.features.inventory.fakes.FakeLocationRepository
import com.alsoug.keswa.features.inventory.fakes.FakePriceRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockReceiptRepository
import com.alsoug.keswa.features.inventory.fakes.FakeVariantRepository
import com.alsoug.keswa.features.inventory.fakes.SequentialIds
import com.alsoug.keswa.features.inventory.fakes.fullPermissionSession
import com.alsoug.keswa.features.inventory.fakes.testDispatchers
import com.alsoug.keswa.features.inventory.presentation.screens.importer.ImportUiEvent
import com.alsoug.keswa.features.inventory.presentation.screens.importer.ImportViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

import com.alsoug.keswa.features.inventory.fakes.FakeCategoryRepository
import com.alsoug.keswa.features.inventory.fakes.FakeColourRepository
import com.alsoug.keswa.features.inventory.fakes.FakeProductRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = testDispatchers(testDispatcher)
    private val sessions = fullPermissionSession()
    private val ids = SequentialIds()
    private val now = { 1_000_000L }

    private val locations = FakeLocationRepository()
    private val products = FakeProductRepository()
    private val colours = FakeColourRepository()
    private val categories = FakeCategoryRepository()
    private val variants = FakeVariantRepository()
    private val prices = FakePriceRepository()
    private val receipts = FakeStockReceiptRepository()

    private val resolveLocation = ResolveStockLocationUseCase(locations)
    private val parse = ParseCatalogueImportUseCase()
    private val startReceipt = StartReceiptUseCase(receipts, sessions, ids, now)
    private val addLine = AddReceiptLineUseCase(receipts, sessions, ids)
    private val postReceipt = PostReceiptUseCase(receipts, sessions, now)
    private val apply = ApplyCatalogueImportUseCase(
        products, colours, categories, variants, prices, sessions,
        startReceipt, addLine, postReceipt, ids, now,
    )

    private fun createViewModel() = ImportViewModel(
        resolveLocation = resolveLocation,
        parse = parse,
        apply = apply,
        dispatchers = dispatchers,
    )

    @Test
    fun `checking valid pasted rows enables apply`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ImportUiEvent.Load)
        advanceUntilIdle()

        val tsv = "product\tproductAr\tcolour\tsku\tcost\tprice\tquantity\n" +
            "Hoodie\tهودي\tGrey\tKSW-HD-001\t280\t450\t10"
        vm.onEvent(ImportUiEvent.TextChanged(tsv))
        vm.onEvent(ImportUiEvent.Check)

        assertTrue(vm.state.value.hasParsed)
        assertEquals(1, vm.state.value.rows.size)
        assertTrue(vm.state.value.canApply)
        assertEquals(0, vm.state.value.problems.size)
    }

    @Test
    fun `checking invalid content rejects and disables apply`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ImportUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ImportUiEvent.TextChanged("invalid,incomplete,row"))
        vm.onEvent(ImportUiEvent.Check)

        assertFalse(vm.state.value.hasParsed)
        assertFalse(vm.state.value.canApply)
        assertTrue(vm.state.value.problems.isNotEmpty())
    }
}
