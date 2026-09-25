package com.alsoug.keswa.features.inventory.presentation

import com.alsoug.keswa.core.domain.model.SellableItem
import com.alsoug.keswa.core.domain.money.Money
import com.alsoug.keswa.features.inventory.domain.usecase.AddReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.DiscardReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnrichReceiptLinesUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.EnsureVariantBarcodeUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.FindStockItemUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.GetReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.InventoryLabelUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.ObserveDraftReceiptsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PostReceiptUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintHangTagsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.PrintSingleVariantLabelUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.RecentReceiptsUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ReceivingUseCases
import com.alsoug.keswa.features.inventory.domain.usecase.RemoveReceiptLineUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.ResolveStockLocationUseCase
import com.alsoug.keswa.features.inventory.domain.usecase.StartReceiptUseCase
import com.alsoug.keswa.features.inventory.fakes.FakeLocationRepository
import com.alsoug.keswa.features.inventory.fakes.FakePriceRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSellableRepository
import com.alsoug.keswa.features.inventory.fakes.FakeSettingsRepository
import com.alsoug.keswa.features.inventory.fakes.FakeStockReceiptRepository
import com.alsoug.keswa.features.inventory.fakes.FakeTransportFactory
import com.alsoug.keswa.features.inventory.fakes.FakeVariantRepository
import com.alsoug.keswa.features.inventory.fakes.SequentialIds
import com.alsoug.keswa.features.inventory.fakes.fullPermissionSession
import com.alsoug.keswa.features.inventory.fakes.testDispatchers
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingNavigation
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingUiEffect
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingUiEvent
import com.alsoug.keswa.features.inventory.presentation.screens.receiving.ReceivingViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ReceivingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = testDispatchers(testDispatcher)
    private val sessions = fullPermissionSession()
    private val ids = SequentialIds()
    private val now = { 1_000_000L }

    private val locations = FakeLocationRepository()
    private val prices = FakePriceRepository()
    private val receiptsRepo = FakeStockReceiptRepository()
    private val variantsRepo = FakeVariantRepository().withBarcode("var-1", "6221112223334")
    private val sellablesRepo = FakeSellableRepository().withItem(
        SellableItem(
            variantId = "var-1",
            productId = "prod-1",
            sku = "TEE-NAVY",
            name = "Navy T-Shirt",
            nameAr = "تيشيرت كحلي",
            colourName = "Navy",
            colourNameAr = "كحلي",
            cost = Money.ofPounds(120),
            price = Money.ofPounds(200),
            onHand = 15,
        ),
        barcode = "6221112223334",
    )
    private val settingsRepo = FakeSettingsRepository()
    private val transports = FakeTransportFactory()

    private val resolveLocation = ResolveStockLocationUseCase(locations)
    private val find = FindStockItemUseCase(sellablesRepo, prices, now)
    private val ensureBarcode = EnsureVariantBarcodeUseCase(variantsRepo)
    private val printSingleLabel = PrintSingleVariantLabelUseCase(variantsRepo, prices, settingsRepo, sessions, transports, ensureBarcode, now)
    private val printHangTags = PrintHangTagsUseCase(receiptsRepo, variantsRepo, prices, settingsRepo, sessions, transports, now)
    private val labels = InventoryLabelUseCases(ensureBarcode, printSingleLabel, printHangTags)

    private val receiving = ReceivingUseCases(
        start = StartReceiptUseCase(receiptsRepo, sessions, ids, now),
        addLine = AddReceiptLineUseCase(receiptsRepo, sessions, ids, ensureBarcode),
        removeLine = RemoveReceiptLineUseCase(receiptsRepo, sessions),
        post = PostReceiptUseCase(receiptsRepo, sessions, now),
        discard = DiscardReceiptUseCase(receiptsRepo, sessions),
        getById = GetReceiptUseCase(receiptsRepo),
        recent = RecentReceiptsUseCase(receiptsRepo),
        observeDrafts = ObserveDraftReceiptsUseCase(receiptsRepo),
    )
    private val enrichLines = EnrichReceiptLinesUseCase(find, variantsRepo)

    private fun createViewModel() = ReceivingViewModel(
        resolveLocation = resolveLocation,
        find = find,
        receiving = receiving,
        enrichLines = enrichLines,
        labels = labels,
        dispatchers = dispatchers,
    )

    @Test
    fun `Load resolves location and populates state`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.recent)
    }

    @Test
    fun `full delivery flow from start to scan, confirm and post`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()

        // 1. Start draft delivery
        vm.onEvent(ReceivingUiEvent.ReferenceChanged("INV-990"))
        vm.onEvent(ReceivingUiEvent.SupplierChanged("Al-Nasr Textiles"))
        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()

        val receipt = vm.state.value.receipt
        assertNotNull(receipt)
        assertEquals("INV-990", receipt.reference)
        assertEquals("Al-Nasr Textiles", receipt.supplierName)
        assertTrue(vm.state.value.isDraft)

        // 2. Scan barcode
        vm.onEvent(ReceivingUiEvent.Scanned("6221112223334"))
        advanceUntilIdle()

        val pending = vm.state.value.pendingItem
        assertNotNull(pending)
        assertEquals("TEE-NAVY", pending.sku)
        assertEquals("1", vm.state.value.quantityEntry)
        assertEquals("120.00", vm.state.value.costEntry)

        // 3. Confirm line
        vm.onEvent(ReceivingUiEvent.QuantityChanged("10"))
        vm.onEvent(ReceivingUiEvent.CostChanged("115.00"))
        vm.onEvent(ReceivingUiEvent.ConfirmLine)
        advanceUntilIdle()

        assertNull(vm.state.value.pendingItem)
        assertEquals(1, vm.state.value.lines.size)
        assertEquals(10, vm.state.value.pieceCount)
        assertEquals(Money.ofPiastres(11_500), vm.state.value.lines.single().unitCost)

        // 4. Post receipt
        vm.onEvent(ReceivingUiEvent.Post)
        advanceUntilIdle()

        assertFalse(vm.state.value.isDraft)
        assertFalse(vm.state.value.isPosting)
        assertEquals(1, vm.state.value.costChanges.size)
    }

    @Test
    fun `discarding a draft clears receipt and navigates Done`() = runTest(testDispatcher) {
        val vm = createViewModel()
        vm.onEvent(ReceivingUiEvent.Load)
        advanceUntilIdle()

        vm.onEvent(ReceivingUiEvent.StartReceipt)
        advanceUntilIdle()
        assertNotNull(vm.state.value.receipt)

        val nav = async { vm.navigation.first() }
        vm.onEvent(ReceivingUiEvent.Discard)
        advanceUntilIdle()

        assertNull(vm.state.value.receipt)
        assertEquals(ReceivingNavigation.Done, nav.await())
    }
}
