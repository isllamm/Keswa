package com.alsoug.keswa.features.inventory.presentation

import com.alsoug.keswa.core.domain.model.MovementReason
import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.usecase.AdjustStockUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.GetVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.InventoryLabelUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.PrintHangTagsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintSingleVariantLabelUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StockHistoryUseCase
import com.alsoug.keswa.features.inventory.fakes.FakeLocationRepository
import com.alsoug.keswa.features.inventory.fakes.FakePriceRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSellableRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSettingsRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockAdjustmentRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockReceiptRepository
import com.alsoug.keswa.features.inventory.fakes.FakeTransportFactory
import com.alsoug.keswa.features.inventory.fakes.FakeVariantRepository
import com.alsoug.keswa.features.inventory.fakes.SequentialIds
import com.alsoug.keswa.features.inventory.fakes.fullPermissionSession
import com.alsoug.keswa.features.inventory.fakes.testDispatchers
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustUiEvent
import com.alsoug.keswa.features.inventory.presentation.screens.adjust.AdjustViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AdjustViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = testDispatchers(testDispatcher)
    private val sessions = fullPermissionSession()
    private val ids = SequentialIds()
    private val now = { 1_000_000L }

    private val locations = FakeLocationRepository()
    private val prices = FakePriceRepository()
    private val adjustmentsRepo = FakeStockAdjustmentRepository()
    private val variantsRepo = FakeVariantRepository().withBarcode("var-shirt", "6221115556667")
    private val sellablesRepo = FakeSellableRepository().withItem(
        SellableItem(
            variantId = "var-shirt",
            productId = "prod-shirt",
            sku = "SHT-WHT-L",
            name = "White Linen Shirt L",
            nameAr = "قميص كتان أبيض كبير",
            colourName = "White",
            colourNameAr = "أبيض",
            cost = Money.ofPounds(200),
            price = Money.ofPounds(350),
            onHand = 12,
        ),
        barcode = "6221115556667",
    )
    private val settingsRepo = FakeSettingsRepository()
    private val transports = FakeTransportFactory()
    private val receiptsRepo = FakeStockReceiptRepository()

    private val resolveLocation = ResolveStockLocationUseCase(locations)
    private val find = FindStockItemUseCase(sellablesRepo, prices, now)
    private val adjust = AdjustStockUseCase(adjustmentsRepo, sessions, ids, now)
    private val history = StockHistoryUseCase(adjustmentsRepo)
    private val getBarcode = GetVariantBarcodeUseCase(variantsRepo)
    private val ensureBarcode = EnsureVariantBarcodeUseCase(variantsRepo)
    private val printSingleLabel = PrintSingleVariantLabelUseCase(variantsRepo, prices, settingsRepo, sessions, transports, ensureBarcode, now)
    private val printHangTags = PrintHangTagsUseCase(receiptsRepo, variantsRepo, prices, settingsRepo, sessions, transports, now)
    private val labels = InventoryLabelUseCases(ensureBarcode, printSingleLabel, printHangTags)

    private fun createViewModel() = AdjustViewModel(
        resolveLocation = resolveLocation,
        find = find,
        adjust = adjust,
        history = history,
        getBarcode = getBarcode,
        labels = labels,
        dispatchers = dispatchers,
    )

    @Test
    fun `scanning item populates details and existing barcode`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(AdjustUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(AdjustUiEvent.Scanned("6221115556667"))
        advanceUntilIdle()

        assertNotNull(vm.state.value.item)
        assertEquals("SHT-WHT-L", vm.state.value.item?.sku)
        assertEquals("6221115556667", vm.state.value.barcode)
    }

    @Test
    fun `applying stock damage writes movement and refreshes history`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(AdjustUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(AdjustUiEvent.Scanned("6221115556667"))
        advanceUntilIdle()

        vm.onEvent(AdjustUiEvent.QuantityChanged("-2"))
        vm.onEvent(AdjustUiEvent.ReasonChanged(MovementReason.DAMAGE))
        vm.onEvent(AdjustUiEvent.NoteChanged("Water damage in storage"))
        vm.onEvent(AdjustUiEvent.Apply)
        advanceUntilIdle()

        assertEquals(1, adjustmentsRepo.movements.size)
        val movement = adjustmentsRepo.movements.single()
        assertEquals(-2, movement.quantity)
        assertEquals(MovementReason.DAMAGE, movement.reason)
        assertEquals("Water damage in storage", movement.note)

        assertEquals(1, vm.state.value.history.size)
        assertEquals("", vm.state.value.quantityEntry)
    }

    @Test
    fun `Clear resets scanned item and history`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(AdjustUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(AdjustUiEvent.Scanned("6221115556667"))
        advanceUntilIdle()
        assertNotNull(vm.state.value.item)

        vm.onEvent(AdjustUiEvent.Clear)
        assertNull(vm.state.value.item)
        assertNull(vm.state.value.barcode)
        assertEquals(0, vm.state.value.history.size)
    }
}
